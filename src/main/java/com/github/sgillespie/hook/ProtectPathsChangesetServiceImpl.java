package com.github.sgillespie.hook;

import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.*;
import com.atlassian.bitbucket.commit.*;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionService;
import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageRequestImpl;
import java.util.function.Function;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProtectPathsChangesetServiceImpl implements ProtectPathsChangesetService {
    public static final PageRequest PAGE_REQUEST = new PageRequestImpl(0, PageRequest.MAX_PAGE_LIMIT);

    private final CommitService commitService;
    private final PermissionService permissionService;
    private final AuthenticationContext bitbucketAuthenticationContext;
    private final SettingsFactoryService settingsFactoryService;

    public static final Function<Changeset, String> CHANGESET_TO_ID =
            new Function<Changeset, String>() {
                @Override
                public String apply(@Nullable Changeset changeset) {
                    return changeset.getToCommit().getId();
                }
            };
    public static final Function<Change, Path> CHANGE_TO_PATH =
            new Function<Change, Path>() {
                @Override
                public Path apply(@Nullable Change change) {
                    return change.getPath();
                }
            };

    public ProtectPathsChangesetServiceImpl(CommitService commitService,
                                            PermissionService permissionService,
                                            AuthenticationContext bitbucketAuthenticationContext,
                                            SettingsFactoryService settingsFactoryService) {
        this.commitService = commitService;
        this.permissionService = permissionService;
        this.bitbucketAuthenticationContext = bitbucketAuthenticationContext;
        this.settingsFactoryService = settingsFactoryService;
    }

    @Override
    public List<String> validateChangesets(Repository repository, Settings settings, String refId, String fromHash,
                                           String toHash) {
        List<String> errors = new ArrayList<>();

        // Admins and excluded users
        if (shouldExcludeUser(settings, repository, bitbucketAuthenticationContext.getCurrentUser()))
            return new ArrayList<>();

        // Get protected paths
        List<String> pathRegexps = settingsFactoryService.getPathPatterns(settings);

        if (!shouldIncludeBranch(settings, refId)) return new ArrayList<>();

        Page<Changeset> changesets = findNewChangeSets(repository, fromHash, toHash);
        for (Changeset changeset : changesets.getValues()) {
            Page<Changeset> detailedChangesets = changesets;
            for (Changeset detailedChangeset : detailedChangesets.getValues()) {
                // Validate the paths
        Page<Path> paths = detailedChangeset.getChanges().<Path>transform(CHANGE_TO_PATH);
                for (Path path : paths.getValues()) {
                    for (String regexp : pathRegexps) {
                        if (path.toString().matches(regexp)) {
                            errors.add(String.format("%s: %s matches restricted path %s", refId,
                                    changeset.getToCommit().getId(), regexp));
                        }
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Returns true if the user is an administrator or an excluded user
     *
     * @param settings the hook settings
     * @param user the currently logged in user
     * @return true if the user is an administrator or an excluded user
     */
    private boolean shouldExcludeUser(Settings settings, Repository repository, ApplicationUser user) {
        Boolean isRepoAdmin = permissionService.hasRepositoryPermission(repository, Permission.REPO_ADMIN);
        Boolean isExcluded = settingsFactoryService.getExcludedUsers(settings).contains(user.getName());

        return isRepoAdmin || isExcluded;
    }

    private Boolean shouldIncludeBranch(Settings settings, String refId) {
        FilterType filterType = settingsFactoryService.getFilterType(settings);
        List<String> branchPatterns = settingsFactoryService.getBranchFilters(settings);

        switch (filterType) {
            case ALL:
                return true;
            case INCLUDE:
                return matchesBranch(branchPatterns, refId);
            case EXCLUDE:
                return !matchesBranch(branchPatterns, refId);
            default:
                return null;
        }
    }

    private Page<Changeset> findDetailedChangeSets(Repository repository, String fromHash, String toHash) {
        return findNewChangeSets(repository, fromHash, toHash);
    }

    private Page<Changeset> findNewChangeSets(final Repository repository, String fromHash, String toHash) {
        CommitsBetweenRequest changesetsRequest = new CommitsBetweenRequest.Builder(repository)
                .exclude(fromHash)
                .include(toHash)
                .build();
        return commitService.getCommitsBetween(changesetsRequest, PAGE_REQUEST).<Changeset>transform(
            new Function<Commit, Changeset>() {
                @Override
                public Changeset apply(@Nullable Commit change) {
                    // todo: optimize this
                    for (Changeset cs : commitService.getChangesets(
                        new ChangesetsRequest.Builder(repository).commitId(change.getId()).build(), PAGE_REQUEST).getValues())
                        return cs;
                    return null;
                }
            });
    }

//    private Page<Changeset> getDetailedChangesets(Repository repository, Page<Changeset> changeSets) {
//        Page<String> changeSetIds = changeSets.<Changeset>transform(CHANGESET_TO_ID);
//
//        ChangesetsRequest detailedChangesetsRequest = new ChangesetsRequest.Builder(repository)
//                .changesetIds(changeSetIds.getValues())
//                .maxChangesPerCommit(PageRequest.MAX_PAGE_LIMIT)
//                .build();
//        return commitService.getDetailedChangesets(detailedChangesetsRequest, PAGE_REQUEST);
//    }

    private boolean matchesBranch(Collection<String> regexps, String refId) {
        for (String branch : regexps) {
            if (refId.matches("refs/heads/" + branch)) return true;
        }

        return false;
    }
}
