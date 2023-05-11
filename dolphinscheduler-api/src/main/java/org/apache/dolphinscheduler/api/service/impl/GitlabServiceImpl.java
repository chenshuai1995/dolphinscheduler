/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.api.service.impl;

import org.apache.dolphinscheduler.api.configuration.CustomConfiguration.DSConfiguration;
import org.apache.dolphinscheduler.api.configuration.CustomConfiguration.GitlabConfiguration;
import org.apache.dolphinscheduler.api.configuration.CustomConfiguration.ResourcesConfiguration;
import org.apache.dolphinscheduler.api.dto.gitlab.Commit;
import org.apache.dolphinscheduler.api.dto.gitlab.PushHook;
import org.apache.dolphinscheduler.api.dto.gitlab.ResourceFile;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.service.GitlabService;
import org.apache.dolphinscheduler.api.service.ResourcesService;
import org.apache.dolphinscheduler.api.service.UsersService;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.utils.FileUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.Resource;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.service.storage.StorageOperate;
import org.apache.dolphinscheduler.spi.enums.ResourceType;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.SneakyThrows;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.util.StopWatch.TaskInfo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Files;

/**
 * gitlab service
 **/
@Service
public class GitlabServiceImpl extends BaseServiceImpl implements GitlabService {

    private static final Logger logger = LoggerFactory.getLogger(GitlabServiceImpl.class);

    @Autowired
    private GitlabConfiguration gitlabConfiguration;

    @Autowired
    private ResourcesConfiguration resourcesConfiguration;

    @Autowired
    private DSConfiguration dsConfiguration;

    private GitLabApi gitLabApi;

    @Autowired
    private ResourcesService resourceService;

    @Autowired
    private UsersService usersService;

    @Autowired(required = false)
    private StorageOperate storageOperate;

    // push event
    @SneakyThrows
    public void pushEvent(PushHook event) {
        logger.info("receive Gitlab push hook request: " + JSONUtils.toJsonString(event));
        String defaultBranch = event.getProject().getDefaultBranch();
        String projectName = event.getProject().getName();
        String url = event.getProject().getWebUrl();
        String namespace = event.getProject().getNamespace().toLowerCase();
        String host = url.replace(namespace.toLowerCase() + "/" + projectName, "");

        if (!event.getRef().equals(String.format("refs/heads/%s", defaultBranch))) {
            logger.info("Non-master branch push event, no update");
            return;
        }

        // init gitlab
        gitLabApi = new GitLabApi(host, gitlabConfiguration.getAccessToken());

        // initialize
        if (event.getCommits() != null
                && event.getCommits().size() == 1
                && event.getCommits().get(0).getMessage().equals("feat: #!init")) {
            logger.info("gitlab repository initialize......");
            initialize(event);
            return;
        }

        processPushEvent(event);
    }

    /**
     * core process push event logic
     * @param event
     */
    private void processPushEvent(PushHook event) {
        List<String> createOrUpdateFiles = new ArrayList<>();
        List<String> deleteFiles = new ArrayList<>();
        for (Commit commit : event.getCommits()) {
            for (String filePath : commit.getAdded()) {
                if (isAllowedFile(filePath)) {
                    logger.info("added file is {}", filePath);
                    createOrUpdateFiles.add(filePath);
                } else {
                    printIgnoreFile(filePath);
                }
            }
            for (String filePath : commit.getModified()) {
                if (isAllowedFile(filePath)) {
                    logger.info("modified file is {}", filePath);
                    createOrUpdateFiles.add(filePath);
                } else {
                    printIgnoreFile(filePath);
                }
            }
            for (String filePath : commit.getRemoved()) {
                if (isAllowedFile(filePath)) {
                    logger.info("removed file is {}", filePath);
                    deleteFiles.add(filePath);
                } else {
                    printIgnoreFile(filePath);
                }
            }
        }

        upgradeResources(event, createOrUpdateFiles);

        deleteResources(event, deleteFiles);

        grantResources(event);
    }

    /**
     * delete resources with push event
     * @param event
     * @param deleteFiles
     */
    private void deleteResources(PushHook event, List<String> deleteFiles) {
        logger.info("delete resources starting......");
        String basePath = getRepositoryBasePath(event);
        int failCount = 0;
        for (String filePath : deleteFiles) {
            filePath = basePath + filePath;
            Resource resource = resourceService.queryResourcesFileInfo(dsConfiguration.getAdmin(), filePath);
            // all user will not see
            try {
                resourceService.delete(getAdminUser(), resource.getId());
            } catch (Exception e) {
                e.printStackTrace();
                failCount++;
                logger.error("delete file error, file is {}, exception is {}", filePath, e.getMessage());
            }
        }
        if (failCount > 0) {
            logger.error("delete file fail count: {}", failCount);
        } else {
            logger.info("delete resources success......");
        }
    }

    /**
     * delete resource with init
     * @param resourceIds 19-70-71-72-73-74,19-70-71-72-73-75,
     */
    private void deleteResources(String resourceIds) {
        logger.info("delete resources starting......");
        Set<Integer> resourceSet = new HashSet<>();

        if (StringUtils.isEmpty(resourceIds)) {
            return;
        }

        for (String resourceId : resourceIds.split(",")) {
            // 19-70-71-72-73-75 -> 70-71-72-73-75
            resourceId = resourceId.substring(resourceId.indexOf("-") + 1);

            for (String id : resourceId.split("-")) {
                resourceSet.add(Integer.valueOf(id));
            }
        }
        List<Integer> resourceSorted = resourceSet.stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        logger.info("resourceSorted count: {}, resourceId: {}", resourceSorted.size(), resourceSorted);

        int failCounts = 0;
        User adminUser = getAdminUser();
        for (Integer resourceId : resourceSorted) {
            try {
                resourceService.delete(adminUser, resourceId);
            } catch (Exception e) {
                e.printStackTrace();
                failCounts++;
                logger.error("delete resources failed, resourceId: {}, exception: {}", resourceId, e.getMessage());
            }
        }

        if (failCounts > 0) {
            logger.error("delete resources failed, fail file counts: {}", failCounts);
        } else {
            logger.info("delete resources success");
        }
    }

    private void grantResources(PushHook event) {
        String resourceIds = getResourceIds(event);
        grantAllUsers(resourceIds);
    }

    private String getRepositoryBasePath(PushHook event) {
        String resourcePrefix = resourcesConfiguration.getPrefix();
        String repositoryName = event.getProject().getName();
        return resourcePrefix + "/" + repositoryName + "/";
    }

    private void upgradeResources(PushHook event, List<String> files) {
        String userName = event.getUserName();
        // last commit message, like Merge branch 'feat-xxx' into 'master'feat: xxSee merge request xx
        String message = event.getCommits().get(event.getCommits().size() - 1).getMessage();

        int failCount = 0;
        files.forEach(filePath -> {
            String fullName = "";
            try {
                String content = readRepositoryFile(event, filePath);
                // 1. create or update resource content
                fullName = getRepositoryBasePath(event) + filePath;
                resourceService.createOrUpdateResource(userName, fullName, message, content);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("upgrade resource fail, userName: {}, fullName: {}, exception is {}", userName, fullName,
                        e.getMessage());
            }
        });
        if (failCount > 0) {
            logger.error("upgrade resource fail count: {}", failCount);
        } else {
            logger.info("upgrade resources success......");
        }
    }

    private void initialize(PushHook event) {
        StopWatch watch = new StopWatch();
        try {
            watch.start("deleteResourceTask");
            // get project all resource ids
            // 19-70-71-72-73-74,19-70-71-72-73-75,
            String resourceIds = getResourceIds(event);
            deleteResources(resourceIds);
            watch.stop();

            watch.start("deleteStorageFileTask");
            String repositoryName = event.getProject().getName();
            storageOperate.delete(getAdminUser().getTenantCode(),
                    resourcesConfiguration.getPrefix() + "/" + repositoryName, true);
            watch.stop();

            watch.start("gitlabFileTask");
            Project project = gitLabApi.getProjectApi().getProject(event.getProject().getNamespace(), repositoryName);
            final List<String> allFiles =
                    getRepositoryAllFiles(project.getId(), "/", event.getProject().getDefaultBranch());
            watch.stop();
            logger.info("{} repository file count is {}", repositoryName, allFiles.size());

            // TODO make sure commit adminUser has login in ds
            watch.start("upgradeResourceTask");
            upgradeResources(event, allFiles);
            watch.stop();

            watch.start("grantResourceTask");
            grantResources(event);
            watch.stop();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("grant user resources fail, exception is {} ", e.getMessage());
        }

        for (TaskInfo taskInfo : watch.getTaskInfo()) {
            logger.info("taskName: {}, time elapsed: {}", taskInfo.getTaskName(), taskInfo.getTimeSeconds());
        }
        logger.info("gitlab repository init finished......, time elapsed: {}", watch.getTotalTimeSeconds());
    }

    private void getRepositoryChildrenFile(ResourceFile repository, List<String> resourceIds, String pid) {
        for (ResourceFile resourceFile : repository.getChildren()) {
            String id = String.valueOf(resourceFile.getId());
            id = pid + "-" + id;
            if (resourceFile.isDirctory()) {
                getRepositoryChildrenFile(resourceFile, resourceIds, id);
            } else {
                if (isAllowedFile(resourceFile.getFullName())) {
                    resourceIds.add(id);
                }
            }
        }
    }

    private String getRepositoryResourceId(List<ResourceFile> resourceFiles, String resourcePrefix,
                                           String repositoryName) {
        String repositoryResourceId = "";
        for (ResourceFile resourceFile : resourceFiles) {
            if (resourceFile.isDirctory() && resourceFile.getName().equalsIgnoreCase(resourcePrefix)) {
                ResourceFile warehouse = resourceFile;
                repositoryResourceId = String.valueOf(warehouse.getId());
                List<ResourceFile> repositories = warehouse.getChildren();
                for (ResourceFile repository : repositories) {
                    if (repository.getName().equalsIgnoreCase(repositoryName)) {
                        repositoryResourceId = repositoryResourceId + "-" + repository.getId();
                        return repositoryResourceId;
                    }
                }
            }
        }
        return repositoryResourceId;
    }

    private ResourceFile getRepository(List<ResourceFile> resourceFiles, String resourcePrefix, String repositoryName) {
        for (ResourceFile resourceFile : resourceFiles) {
            if (resourceFile.isDirctory() && resourceFile.getName().equalsIgnoreCase(resourcePrefix)) {
                ResourceFile warehouse = resourceFile;
                List<ResourceFile> repositories = warehouse.getChildren();
                for (ResourceFile repository : repositories) {
                    if (repository.getName().equalsIgnoreCase(repositoryName)) {
                        return repository;
                    }
                }
            }
        }
        return null;
    }

    private boolean isAllowedFile(String fullName) {
        String nameSuffix = Files.getFileExtension(fullName);
        String resourceViewSuffixes = FileUtils.getResourceViewSuffixes();
        if (StringUtils.isNotEmpty(resourceViewSuffixes)) {
            List<String> allowedSuffix = Arrays.asList(resourceViewSuffixes.split(","));
            if (allowedSuffix.contains(nameSuffix)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getRepositoryAllFiles(final Integer projectId, final String directory, final String branch) {
        List<String> fileNames = new ArrayList<>();
        try {
            gitLabApi.getRepositoryApi().getTree(projectId, directory, branch).forEach(file -> {
                try {
                    // if current type is directory, get all file from this directory
                    if (file.getType().equals(TreeItem.Type.TREE)) {
                        fileNames.addAll(getRepositoryAllFiles(projectId, file.getPath(), branch));
                        return;
                    }
                    // if current type is file, add it to fileNames
                    if (isAllowedFile(file.getName())) {
                        String filePath = String.join("/", directory, file.getName());
                        if (filePath.startsWith("//")) {
                            filePath = filePath.replace("//", "");
                        }
                        fileNames.add(filePath);
                    } else {
                        printIgnoreFile(file.getName());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("get file {} error {}", file, e.getMessage());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("get gitlab repository file error {}", e.getMessage());
        }
        return fileNames;
    }

    private String readRepositoryFile(PushHook event, String filePath) {
        try {
            Project project = gitLabApi.getProjectApi().getProject(event.getProject().getNamespace(),
                    event.getProject().getName());
            InputStream inputStream =
                    gitLabApi.getRepositoryFileApi().getRawFile(project.getId(), project.getDefaultBranch(), filePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            while (reader.ready()) {
                String lineContent = reader.readLine();
                lineContent = lineContent.replace(" ", " ");
                stringBuilder.append(lineContent + "\r\n");
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            logger.error("read repository file error, file is {}, exception is {}", filePath, e.getMessage());
        }
        return null;
    }

    private User getAdminUser() {
        return usersService.getUserByUserName(dsConfiguration.getAdmin());
    }

    private List<User> getAllUsers() {
        User adminUser = getAdminUser();
        Map<String, Object> userResult = usersService.queryUserList(adminUser);
        if (userResult.get(Constants.STATUS).toString().equalsIgnoreCase(Status.SUCCESS.getMsg())) {
            List<User> users = (List<User>) userResult.get(Constants.DATA_LIST);
            logger.info("all users : {}", users);
            return users;
        }
        return null;
    }

    private void grantAllUsers(String resourceIds) {
        List<User> users = getAllUsers();
        int failCounts = 0;
        for (User user : users) {
            Map<String, Object> grantResourcesResult = usersService.grantResources(user, user.getId(), resourceIds);
            if (!grantResourcesResult.get(Constants.STATUS).toString().equalsIgnoreCase(Status.SUCCESS.getMsg())) {
                Object errorMsg = grantResourcesResult.get(Constants.MSG);
                int resourceIdValue = (int) grantResourcesResult.get(Constants.DATA_LIST);
                logger.error("grant user resource fail, userName: {}, errorMsg: {}, failResourceId: {}",
                        user.getUserName(), errorMsg, resourceIdValue);
                failCounts++;
            }
        }
        if (failCounts > 0) {
            logger.error("grant user resource failed, fail file counts: {}", failCounts);
        } else {
            logger.info("grant user resources success......");
        }
    }

    private String getResourceIds(PushHook event) {
        List<String> resourceIdList = new ArrayList<>();

        String resourcePrefix = resourcesConfiguration.getPrefix();
        String repositoryName = event.getProject().getName();

        User adminUser = getAdminUser();
        Map<String, Object> resourceResult = resourceService.queryResourceList(adminUser, ResourceType.FILE);
        if (resourceResult.get(Constants.STATUS).toString().equalsIgnoreCase(Status.SUCCESS.getMsg())) {
            ObjectNode jsonNodes = JSONUtils.parseObject(JSONUtils.toJsonString(resourceResult));
            List<ResourceFile> resourceFiles =
                    JSONUtils.toList(jsonNodes.get(Constants.DATA_LIST).toString(), ResourceFile.class);

            ResourceFile repository = getRepository(resourceFiles, resourcePrefix.substring(1), repositoryName);
            logger.info("repository: {}", repository.getFullName());
            String repositoryResourceId =
                    getRepositoryResourceId(resourceFiles, resourcePrefix.substring(1), repositoryName);
            logger.info("repositoryResourceId: {}", repositoryResourceId);

            if (repository != null) {
                getRepositoryChildrenFile(repository, resourceIdList, repositoryResourceId);
            }
        }
        String resourceIds = resourceIdList.stream().collect(Collectors.joining(","));
        logger.info("resourceIds: {}", resourceIds);
        return resourceIds;
    }

    private void printIgnoreFile(String filePath) {
        String nameSuffix = Files.getFileExtension(filePath);
        String resourceViewSuffixes = FileUtils.getResourceViewSuffixes();
        logger.info(
                "ignore file: {}, resource suffix {} not support view, allowed file suffix is: {}",
                filePath, nameSuffix,
                resourceViewSuffixes);
    }
}
