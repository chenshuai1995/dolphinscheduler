package org.apache.dolphinscheduler.api.service;

import org.apache.dolphinscheduler.api.dto.gitlab.PushHook;

/**
 * gitlab service
 **/
public interface GitlabService {

    void pushEvent(PushHook event);
}
