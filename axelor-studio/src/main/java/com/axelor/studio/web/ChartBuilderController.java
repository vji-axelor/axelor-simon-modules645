/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2023 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.studio.web;

import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.studio.service.ChartRecordViewService;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class ChartBuilderController {

  public void viewRelatedRecord(ActionRequest request, ActionResponse response) {
    String chartName = (String) request.getContext().get("_chart");
    try {
      Map<String, Object> context = request.getContext();
      Map<String, Object> actionView =
          Beans.get(ChartRecordViewService.class).getActionView(chartName, context);
      response.setView(actionView);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }
}
