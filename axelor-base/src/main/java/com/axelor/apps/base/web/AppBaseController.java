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
package com.axelor.apps.base.web;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.apps.base.service.MapService;
import com.axelor.apps.base.service.administration.ExportDbObjectService;
import com.axelor.apps.base.service.currency.CurrencyConversionFactory;
import com.axelor.apps.base.service.currency.CurrencyConversionService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.quartz.JobRunner;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class AppBaseController {

  public void exportObjects(ActionRequest request, ActionResponse response) {
    MetaFile metaFile = Beans.get(ExportDbObjectService.class).exportObject();
    if (metaFile == null) {
      response.setFlash(I18n.get(BaseExceptionMessage.GENERAL_4));
    } else {
      response.setView(
          ActionView.define(I18n.get(BaseExceptionMessage.GENERAL_5))
              .model("com.axelor.meta.db.MetaFile")
              .add("form", "meta-files-form")
              .add("grid", "meta-files-grid")
              .param("forceEdit", "true")
              .context("_showRecord", metaFile.getId().toString())
              .map());
    }
  }

  public void checkMapApi(ActionRequest request, ActionResponse response) {
    try {
      AppBase appBase = request.getContext().asType(AppBase.class);
      Integer apiType = appBase.getMapApiSelect();

      if (apiType == 1) {
        Beans.get(MapService.class).testGMapService();
        response.setFlash(BaseExceptionMessage.GENERAL_6);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void updateCurrencyConversion(ActionRequest request, ActionResponse response) {
    try {
      CurrencyConversionService currencyConversionService =
          Beans.get(CurrencyConversionFactory.class).getCurrencyConversionService();
      currencyConversionService.updateCurrencyConverion();
      response.setReload(true);

    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void applyApplicationMode(ActionRequest request, ActionResponse response) {
    String applicationMode = AppSettings.get().get("application.mode", "prod");
    if ("dev".equals(applicationMode)) {
      response.setAttr("mainPanel", "hidden", false);
    }
  }

  public void showCustomersOnMap(ActionRequest request, ActionResponse response) {
    try {
      response.setView(
          ActionView.define(I18n.get("Customers"))
              .add("html", Beans.get(MapService.class).getMapURI("customer"))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void showProspectsOnMap(ActionRequest request, ActionResponse response) {
    try {
      response.setView(
          ActionView.define(I18n.get("Prospects"))
              .add("html", Beans.get(MapService.class).getMapURI("prospect"))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void showSuppliersOnMap(ActionRequest request, ActionResponse response) {
    try {
      response.setView(
          ActionView.define(I18n.get("Suppliers"))
              .add("html", Beans.get(MapService.class).getMapURI("supplier"))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void checkQuartzScheduler(ActionRequest request, ActionResponse response) {
    if (Beans.get(JobRunner.class).isEnabled()) {
      response.setFlash(I18n.get(BaseExceptionMessage.QUARTZ_SCHEDULER_ENABLED));
    }
  }
}
