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

package org.apache.dolphinscheduler.api.controller;

import org.apache.dolphinscheduler.api.dto.gitlab.PushHook;
import org.apache.dolphinscheduler.api.service.GitlabService;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.common.utils.JSONUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;

/**
 * gitlab controller
 */
@Api(tags = "gitlab_TAG")
@RestController
@RequestMapping("gitlab")
public class GitlabController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(GitlabController.class);

    private static final String PUSH_EVENT = "Push Hook";
    private static final String MERGE_REQUEST_EVENT = "Merge Request Hook";

    @Autowired
    private GitlabService gitlabService;

    @PostMapping("/event/hook")
    public Result<String> pushEvent(@RequestHeader("X-Gitlab-Event") String event,
                                    @RequestBody Object json) {
        logger.info("receive Gitlab Web hook request: {}", JSONUtils.toJsonString(json));

        if (PUSH_EVENT.equals(event)) {
            PushHook pushHook = JSONUtils.parseObject(JSONUtils.toJsonString(json), PushHook.class);
            gitlabService.pushEvent(pushHook);
        } else {
            logger.warn("not supported this event {}", event);
        }

        return Result.success();
    }

}
