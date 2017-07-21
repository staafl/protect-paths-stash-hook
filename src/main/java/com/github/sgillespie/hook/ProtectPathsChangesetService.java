package com.github.sgillespie.hook;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.Settings;

import java.util.List;

public interface ProtectPathsChangesetService {
    public List<String> validateChangesets(Repository repository,
                                           Settings settings,
                                           String refId,
                                           String fromHash,
                                           String toHash);
}
