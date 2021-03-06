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
// @flow
import { onFail } from '../../store/rootActions';
import { parseIssueFromResponse } from '../../helpers/issues';
import type { Issue } from './types';

export const updateIssue = (
  onChange: Issue => void,
  resultPromise: Promise<*>,
  oldIssue?: Issue,
  newIssue?: Issue
) => {
  const optimisticUpdate = oldIssue != null && newIssue != null;

  if (optimisticUpdate) {
    // $FlowFixMe `newIssue` is not null, because `optimisticUpdate` is true
    onChange(newIssue);
  }

  resultPromise.then(
    response => {
      if (!optimisticUpdate) {
        const issue = parseIssueFromResponse(
          response.issue,
          response.components,
          response.users,
          response.rules
        );
        onChange(issue);
      }
    },
    error => {
      onFail(error);
      if (optimisticUpdate) {
        // $FlowFixMe `oldIssue` is not null, because `optimisticUpdate` is true
        onChange(oldIssue);
      }
    }
  );
};
