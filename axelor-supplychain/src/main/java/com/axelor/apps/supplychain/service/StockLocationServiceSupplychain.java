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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.exception.AxelorException;
import com.axelor.meta.CallMethod;
import java.math.BigDecimal;

public interface StockLocationServiceSupplychain extends StockLocationService {

  @CallMethod
  public BigDecimal getReservedQty(Long productId, Long locationId, Long companyId)
      throws AxelorException;
}
