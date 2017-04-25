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
package org.sonar.server.organization.ws;

import org.junit.Test;
import org.sonar.api.server.ws.WebService;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.server.organization.ws.OrganizationsWsSupport.PARAM_ORGANIZATION;
import static org.sonar.server.organization.ws.UpdateProjectVisibilityAction.ACTION;
import static org.sonar.server.organization.ws.UpdateProjectVisibilityAction.PARAM_PROJECT_VISIBILITY;

public class UpdateProjectVisibilityActionTest {
  private UpdateProjectVisibilityAction underTest = new UpdateProjectVisibilityAction();
  private WsActionTester wsTester = new WsActionTester(underTest);

  @Test
  public void verify_define() {
    WebService.Action action = wsTester.getDef();
    assertThat(action.key()).isEqualTo(ACTION);
    assertThat(action.isPost()).isTrue();
    assertThat(action.description()).isNotEmpty();
    assertThat(action.isInternal()).isTrue();
    assertThat(action.since()).isEqualTo("6.4");
    assertThat(action.handler()).isEqualTo(underTest);
    assertThat(action.changelog()).isEmpty();

    WebService.Param organization = action.param(PARAM_ORGANIZATION);
    assertThat(organization.isRequired()).isTrue();
    assertThat(organization.exampleValue()).isEqualTo("foo-company");
    assertThat(organization.description()).isEqualTo("Organization key");

    WebService.Param projectVisibility = action.param(PARAM_PROJECT_VISIBILITY);
    assertThat(projectVisibility.isRequired()).isTrue();
    assertThat(projectVisibility.possibleValues()).containsExactlyInAnyOrder("private", "public");
    assertThat(projectVisibility.description()).isEqualTo("Default visibility for projects");
  }
}
