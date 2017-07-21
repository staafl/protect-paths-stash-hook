package com.github.sgillespie.hook;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.RepositorySettingsValidator;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;

public class ProtectPathsSettingsValidator  implements RepositorySettingsValidator {
    @Override
    public void validate(@Nonnull Settings settings,
                         @Nonnull SettingsValidationErrors settingsValidationErrors,
                         @Nonnull Repository repository) {
        if (StringUtils.isEmpty(settings.getString("pathPatterns"))) {
            settingsValidationErrors.addFieldError("pathPatterns", "Path Patterns is mandatory");
        }
    }
}
