/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.adapter;

import com.google.common.base.Objects;

import java.util.List;

public class LocalOverrideRequest {
    private List<String> rootComponentNames;

    private String recipeFolder;
    private String artifactFolder;

    // TODO Does DeploymentService supports it?
    // private List<String> rootComponentNamesToRemove;


    public LocalOverrideRequest(List<String> rootComponentNames, String recipeFolder, String artifactFolder) {
        this.rootComponentNames = rootComponentNames;
        this.recipeFolder = recipeFolder;
        this.artifactFolder = artifactFolder;
    }

    public List<String> getRootComponentNames() {
        return rootComponentNames;
    }

    public String getRecipeFolder() {
        return recipeFolder;
    }

    public String getArtifactFolder() {
        return artifactFolder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LocalOverrideRequest that = (LocalOverrideRequest) o;
        return Objects.equal(rootComponentNames, that.rootComponentNames) && Objects
                .equal(recipeFolder, that.recipeFolder) && Objects.equal(artifactFolder, that.artifactFolder);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rootComponentNames, recipeFolder, artifactFolder);
    }
}
