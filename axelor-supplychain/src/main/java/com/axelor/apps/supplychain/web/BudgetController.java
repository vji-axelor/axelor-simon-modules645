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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.account.db.Budget;
import com.axelor.apps.account.db.repo.BudgetRepository;
import com.axelor.apps.supplychain.service.BudgetSupplychainService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class BudgetController {

  public void computeTotalAmountCommited(ActionRequest request, ActionResponse response) {
    try {
      Budget budget = request.getContext().asType(Budget.class);
      budget = Beans.get(BudgetRepository.class).find(budget.getId());
      response.setValue(
          "totalAmountCommitted",
          Beans.get(BudgetSupplychainService.class).computeTotalAmountCommitted(budget));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
