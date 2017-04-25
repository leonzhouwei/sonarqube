/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.project.ws;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Scopes;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.permission.GroupPermissionDto;
import org.sonar.db.permission.UserPermissionDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.permission.index.PermissionIndexer;
import org.sonar.server.user.UserSession;

import static java.util.Collections.singletonList;
import static org.sonar.core.permission.ProjectPermissions.PUBLIC_PERMISSIONS;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;
import static org.sonar.server.ws.WsUtils.checkRequest;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_PROJECT;

public class ChangeVisibilityAction implements ProjectsWsAction {
  private static final String ACTION = "change_visibility";
  private static final String PARAM_VISIBILITY = "visibility";
  private static final String PUBLIC_VISIBILITY = "public";
  private static final String PRIVATE_VISIBILITY = "private";
  private static final Set<String> ALLOWED_QUALIFIERS = ImmutableSet.of(Qualifiers.PROJECT, Qualifiers.VIEW);

  private final DbClient dbClient;
  private final ComponentFinder componentFinder;
  private final UserSession userSession;
  private final PermissionIndexer permissionIndexer;

  public ChangeVisibilityAction(DbClient dbClient, ComponentFinder componentFinder, UserSession userSession,
    PermissionIndexer permissionIndexer) {
    this.dbClient = dbClient;
    this.componentFinder = componentFinder;
    this.userSession = userSession;
    this.permissionIndexer = permissionIndexer;
  }

  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION)
      .setDescription("Change visibility of a project or a view.<br/>" +
        "Requires 'Project administer' permission on the specified project or view<br/>")
      .setSince("6.4")
      .setPost(true)
      .setHandler(this);

    action.createParam(PARAM_PROJECT)
      .setDescription("Project or view key")
      .setExampleValue(KEY_PROJECT_EXAMPLE_001)
      .setRequired(true);

    action.createParam(PARAM_VISIBILITY)
      .setDescription("new visibility of the project or view")
      .setPossibleValues(PUBLIC_VISIBILITY, PRIVATE_VISIBILITY)
      .setRequired(true);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkLoggedIn();

    String projectKey = request.mandatoryParam(PARAM_PROJECT);
    boolean changeToPrivate = PRIVATE_VISIBILITY.equals(request.mandatoryParam(PARAM_VISIBILITY));

    try (DbSession dbSession = dbClient.openSession(false)) {
      ComponentDto component = componentFinder.getByKey(dbSession, projectKey);
      checkRequest(isRoot(component), "Component must either be a project or a view");
      checkRequest(!changeToPrivate || !Qualifiers.VIEW.equals(component.qualifier()), "Views can't be made private");
      userSession.checkComponentPermission(UserRole.ADMIN, component);
      checkRequest(noPendingTask(dbSession, component), "Component visibility can't be changed as long as it has background task(s) pending or in progress");

      if (changeToPrivate != component.isPrivate()) {
        dbClient.componentDao().setPrivateForRootComponentUuid(dbSession, component.uuid(), changeToPrivate);
        if (changeToPrivate) {
          updatePermissionsToPrivate(dbSession, component);
        } else {
          updatePermissionsToPublic(dbSession, component);
        }
        dbSession.commit();
        permissionIndexer.indexProjectsByUuids(dbSession, singletonList(component.uuid()));
      }
    }
  }

  private static boolean isRoot(ComponentDto component) {
    return Scopes.PROJECT.equals(component.scope()) && ALLOWED_QUALIFIERS.contains(component.qualifier());
  }

  private boolean noPendingTask(DbSession dbSession, ComponentDto rootComponent) {
    return dbClient.ceQueueDao().selectByComponentUuid(dbSession, rootComponent.uuid()).isEmpty();
  }

  private void updatePermissionsToPrivate(DbSession dbSession, ComponentDto component) {
    // delete project permissions for group AnyOne
    dbClient.groupPermissionDao().deleteByRootComponentIdAndGroupId(dbSession, component.getId(), null);
    // grant UserRole.CODEVIEWER and UserRole.USER to any group or user with at least one permission on project
    PUBLIC_PERMISSIONS.forEach(permission -> {
      dbClient.groupPermissionDao().selectGroupIdsWithPermissionOnProjectBut(dbSession, component.getId(), permission)
        .forEach(groupId -> insertProjectPermissionOnGroup(dbSession, component, permission, groupId));
      dbClient.userPermissionDao().selectUserIdsWithPermissionOnProjectBut(dbSession, component.getId(), permission)
        .forEach(userId -> insertProjectPermissionOnUser(dbSession, component, permission, userId));
    });
  }

  private void insertProjectPermissionOnUser(DbSession dbSession, ComponentDto component, String permission, Integer userId) {
    dbClient.userPermissionDao().insert(dbSession, new UserPermissionDto(component.getOrganizationUuid(), permission, userId, component.getId()));
  }

  private void insertProjectPermissionOnGroup(DbSession dbSession, ComponentDto component, String permission, Integer groupId) {
    dbClient.groupPermissionDao().insert(dbSession, new GroupPermissionDto()
      .setOrganizationUuid(component.getOrganizationUuid())
      .setResourceId(component.getId())
      .setGroupId(groupId)
      .setRole(permission));
  }

  private void updatePermissionsToPublic(DbSession dbSession, ComponentDto component) {
    PUBLIC_PERMISSIONS.forEach(permission -> {
      // delete project group permission for UserRole.CODEVIEWER and UserRole.USER
      dbClient.groupPermissionDao().deleteByRootComponentIdAndPermission(dbSession, component.getId(), permission);
      // delete project user permission for UserRole.CODEVIEWER and UserRole.USER
      dbClient.userPermissionDao().deleteProjectPermissionOfAnyUser(dbSession, component.getId(), permission);
    });
  }

}
