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
package org.sonar.server.component.ws;

import java.util.Objects;
import java.util.Optional;
import org.sonar.api.resources.Qualifiers;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonarqube.ws.WsComponents;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.emptyToNull;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.core.util.Protobuf.setNullable;

class ComponentDtoToWsComponent {
  private ComponentDtoToWsComponent() {
    // prevent instantiation
  }

  static WsComponents.Component.Builder componentDtoToWsComponent(ComponentDto dto, OrganizationDto organizationDto, Optional<SnapshotDto> lastAnalysis) {
    checkArgument(
      Objects.equals(dto.getOrganizationUuid(), organizationDto.getUuid()),
      "OrganizationUuid (%s) of ComponentDto to convert to Ws Component is not the same as the one (%s) of the specified OrganizationDto",
      dto.getOrganizationUuid(), organizationDto.getUuid());
    return componentDtoToWsComponent(dto, organizationDto.getKey(), lastAnalysis);
  }

  private static WsComponents.Component.Builder componentDtoToWsComponent(ComponentDto dto, String organizationDtoKey, Optional<SnapshotDto> lastAnalysis) {
    WsComponents.Component.Builder wsComponent = WsComponents.Component.newBuilder()
      .setOrganization(organizationDtoKey)
      .setId(dto.uuid())
      .setKey(dto.key())
      .setName(dto.name())
      .setQualifier(dto.qualifier());
    setNullable(emptyToNull(dto.path()), wsComponent::setPath);
    setNullable(emptyToNull(dto.description()), wsComponent::setDescription);
    setNullable(emptyToNull(dto.language()), wsComponent::setLanguage);
    setTags(dto, wsComponent);
    lastAnalysis.ifPresent(analysis -> wsComponent.setAnalysisDate(formatDateTime(analysis.getCreatedAt())));
    if (Qualifiers.PROJECT.equals(dto.qualifier())) {
      wsComponent.setVisibility(dto.isPrivate() ? "private" : "public");
    }
    return wsComponent;
  }

  private static void setTags(ComponentDto dto, WsComponents.Component.Builder wsComponent) {
    if (Qualifiers.PROJECT.equals(dto.qualifier())) {
      wsComponent.getTagsBuilder().addAllTags(dto.getTags());
    }
  }
}
