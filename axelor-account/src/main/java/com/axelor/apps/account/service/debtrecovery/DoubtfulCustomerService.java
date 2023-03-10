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
package com.axelor.apps.account.service.debtrecovery;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.InvoiceTermRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.FiscalPositionAccountService;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveCreateService;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.account.service.moveline.MoveLineCreateService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoubtfulCustomerService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected MoveCreateService moveCreateService;
  protected MoveValidateService moveValidateService;
  protected MoveToolService moveToolService;
  protected MoveRepository moveRepo;
  protected MoveLineCreateService moveLineCreateService;
  protected MoveLineRepository moveLineRepo;
  protected ReconcileService reconcileService;
  protected AccountConfigService accountConfigService;
  protected DoubtfulCustomerInvoiceTermService doubtfulCustomerInvoiceTermService;
  protected AppBaseService appBaseService;
  protected InvoiceTermRepository invoiceTermRepo;

  @Inject
  public DoubtfulCustomerService(
      MoveCreateService moveCreateService,
      MoveValidateService moveValidateService,
      MoveToolService moveToolService,
      MoveRepository moveRepo,
      MoveLineCreateService moveLineCreateService,
      MoveLineRepository moveLineRepo,
      ReconcileService reconcileService,
      AccountConfigService accountConfigService,
      DoubtfulCustomerInvoiceTermService doubtfulCustomerInvoiceTermService,
      AppBaseService appBaseService,
      InvoiceTermRepository invoiceTermRepo) {

    this.moveCreateService = moveCreateService;
    this.moveValidateService = moveValidateService;
    this.moveToolService = moveToolService;
    this.moveRepo = moveRepo;
    this.moveLineCreateService = moveLineCreateService;
    this.moveLineRepo = moveLineRepo;
    this.reconcileService = reconcileService;
    this.accountConfigService = accountConfigService;
    this.doubtfulCustomerInvoiceTermService = doubtfulCustomerInvoiceTermService;
    this.appBaseService = appBaseService;
    this.invoiceTermRepo = invoiceTermRepo;
  }

  /**
   * Proc??dure permettant de v??rifier le remplissage des champs dans la soci??t??, n??cessaire au
   * traitement du passage en client douteux
   *
   * @param company Une soci??t??
   * @throws AxelorException
   */
  public void testCompanyField(Company company) throws AxelorException {

    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

    accountConfigService.getDoubtfulCustomerAccount(accountConfig);
    accountConfigService.getAutoMiscOpeJournal(accountConfig);
    accountConfigService.getSixMonthDebtPassReason(accountConfig);
    accountConfigService.getThreeMonthDebtPassReason(accountConfig);
  }

  /**
   * Proc??dure permettant de cr??er les ??critures de passage en client douteux pour chaque ??criture
   * de facture
   *
   * @param moveList Une liste d'??critures de facture
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   * @throws AxelorException
   */
  public void createDoubtFulCustomerMove(
      List<Move> moveList, Account doubtfulCustomerAccount, String debtPassReason)
      throws AxelorException {

    for (Move move : moveList) {

      this.createDoubtFulCustomerMove(move, doubtfulCustomerAccount, debtPassReason);
    }
  }

  public List<MoveLine> getInvoicePartnerMoveLines(Move move, Account doubtfulCustomerAccount) {
    List<MoveLine> moveLineList = new ArrayList<MoveLine>();
    for (MoveLine moveLine : move.getMoveLineList()) {
      if (moveLine.getAccount() != null
          && moveLine.getAccount().getUseForPartnerBalance()
          && moveLine.getAmountRemaining().signum() > 0
          && !moveLine.getAccount().equals(doubtfulCustomerAccount)
          && moveLine.getDebit().signum() > 0) {
        moveLineList.add(moveLine);
      }
    }

    return moveLineList;
  }

  /**
   * Proc??dure permettant de cr??er les ??critures de passage en client douteux pour chaque ??criture
   * de facture
   *
   * @param move Une ??critures de facture
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void createDoubtFulCustomerMove(
      Move move, Account doubtfulCustomerAccount, String debtPassReason) throws AxelorException {
    Company company = move.getCompany();
    Partner partner = move.getPartner();
    Invoice invoice = move.getInvoice();

    Move newMove =
        moveCreateService.createMove(
            company.getAccountConfig().getAutoMiscOpeJournal(),
            company,
            move.getCurrency(),
            partner,
            move.getPaymentMode(),
            invoice != null
                ? invoice.getFiscalPosition()
                : (partner != null ? partner.getFiscalPosition() : null),
            MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC,
            MoveRepository.FUNCTIONAL_ORIGIN_DOUBTFUL_CUSTOMER,
            move.getOrigin(),
            debtPassReason,
            invoice != null ? invoice.getCompanyBankDetails() : move.getCompanyBankDetails());
    newMove.setOriginDate(move.getOriginDate());
    newMove.setInvoice(invoice);
    LocalDate todayDate = appBaseService.getTodayDate(company);

    List<MoveLine> invoicePartnerMoveLines =
        this.getInvoicePartnerMoveLines(move, doubtfulCustomerAccount);

    String origin = "";
    BigDecimal amountRemaining = BigDecimal.ZERO;
    List<MoveLine> creditMoveLines = new ArrayList<MoveLine>();
    if (invoicePartnerMoveLines != null) {
      for (MoveLine moveLine : invoicePartnerMoveLines) {
        amountRemaining = amountRemaining.add(moveLine.getAmountRemaining());
        // Credit move line on partner account
        MoveLine creditMoveLine =
            moveLineCreateService.createMoveLine(
                newMove,
                partner,
                moveLine.getAccount(),
                moveLine.getAmountRemaining(),
                false,
                todayDate,
                1,
                move.getOrigin(),
                debtPassReason);

        origin = creditMoveLine.getOrigin();
        creditMoveLines.add(creditMoveLine);
      }
    }

    // Debit move line on doubtful customer account
    MoveLine debitMoveLine =
        moveLineCreateService.createMoveLine(
            newMove,
            partner,
            doubtfulCustomerAccount,
            amountRemaining,
            true,
            todayDate,
            2,
            origin,
            debtPassReason);
    debitMoveLine.setPassageReason(debtPassReason);

    doubtfulCustomerInvoiceTermService.createOrUpdateInvoiceTerms(
        invoice,
        newMove,
        invoicePartnerMoveLines,
        creditMoveLines,
        debitMoveLine,
        todayDate,
        amountRemaining);

    this.invoiceProcess(newMove, doubtfulCustomerAccount, debtPassReason);
  }

  public void createDoubtFulCustomerRejectMove(
      List<MoveLine> moveLineList, Account doubtfulCustomerAccount, String debtPassReason)
      throws AxelorException {

    for (MoveLine moveLine : moveLineList) {

      this.createDoubtFulCustomerRejectMove(moveLine, doubtfulCustomerAccount, debtPassReason);
    }
  }

  /**
   * Proc??dure permettant de cr??er les ??critures de passage en client douteux pour chaque ligne
   * d'??criture de rejet de facture
   *
   * @param moveLine Une ligne d'??critures de rejet de facture
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void createDoubtFulCustomerRejectMove(
      MoveLine moveLine, Account doubtfulCustomerAccount, String debtPassReason)
      throws AxelorException {

    log.debug("Concerned move : {} ", moveLine.getName());
    Company company = moveLine.getMove().getCompany();
    Partner partner = moveLine.getPartner();
    LocalDate todayDate = appBaseService.getTodayDate(company);

    Move newMove =
        moveCreateService.createMove(
            company.getAccountConfig().getAutoMiscOpeJournal(),
            company,
            null,
            partner,
            moveLine.getMove().getPaymentMode(),
            partner != null ? partner.getFiscalPosition() : null,
            MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC,
            MoveRepository.FUNCTIONAL_ORIGIN_DOUBTFUL_CUSTOMER,
            moveLine.getName(),
            debtPassReason,
            moveLine.getMove().getCompanyBankDetails());

    BigDecimal amountRemaining = moveLine.getAmountRemaining();

    // Ecriture au cr??dit sur le 411
    MoveLine creditMoveLine =
        moveLineCreateService.createMoveLine(
            newMove,
            partner,
            moveLine.getAccount(),
            amountRemaining,
            false,
            todayDate,
            1,
            moveLine.getName(),
            debtPassReason);
    newMove.addMoveLineListItem(creditMoveLine);

    Reconcile reconcile =
        reconcileService.createReconcile(moveLine, creditMoveLine, amountRemaining, false);
    if (reconcile != null) {
      reconcileService.confirmReconcile(reconcile, true, true);
    }

    // Ecriture au d??bit sur le 416 (client douteux)
    MoveLine debitMoveLine =
        moveLineCreateService.createMoveLine(
            newMove,
            newMove.getPartner(),
            doubtfulCustomerAccount,
            amountRemaining,
            true,
            todayDate,
            2,
            moveLine.getName(),
            debtPassReason);
    newMove.getMoveLineList().add(debitMoveLine);

    debitMoveLine.setInvoiceReject(moveLine.getInvoiceReject());
    debitMoveLine.setPassageReason(debtPassReason);

    moveValidateService.accounting(newMove);
    moveRepo.save(newMove);

    this.invoiceRejectProcess(debitMoveLine, doubtfulCustomerAccount, debtPassReason);
  }

  /**
   * Proc??dure permettant de mettre ?? jour le motif de passage en client douteux, et cr??er
   * l'??v??nement li??.
   *
   * @param moveList Une liste d'??citure de facture
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   */
  public void updateDoubtfulCustomerMove(
      List<Move> moveList, Account doubtfulCustomerAccount, String debtPassReason) {

    for (Move move : moveList) {

      for (MoveLine moveLine : move.getMoveLineList()) {

        if (moveLine.getAccount().equals(doubtfulCustomerAccount)
            && moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0) {

          moveLine.setPassageReason(debtPassReason);
          moveLineRepo.save(moveLine);

          break;
        }
      }
    }
  }

  /**
   * Proc??dure permettant de mettre ?? jour les champs de la facture avec la nouvelle ??criture de
   * d??bit sur le compte 416
   *
   * @param move La nouvelle ??criture de d??bit sur le compte 416
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   * @throws AxelorException
   */
  public Invoice invoiceProcess(Move move, Account doubtfulCustomerAccount, String debtPassReason)
      throws AxelorException {

    Invoice invoice = move.getInvoice();

    if (invoice != null) {

      invoice.setOldMove(invoice.getMove());
      invoice.setMove(move);
      FiscalPosition fiscalPosition = invoice.getFiscalPosition();

      if (invoice.getPartner() != null) {
        doubtfulCustomerAccount =
            Beans.get(FiscalPositionAccountService.class)
                .getAccount(fiscalPosition, doubtfulCustomerAccount);
      }
      invoice.setPartnerAccount(doubtfulCustomerAccount);
      invoice.setDoubtfulCustomerOk(true);
      // Recalcule du restant ?? payer de la facture
      invoice.setCompanyInTaxTotalRemaining(moveToolService.getInTaxTotalRemaining(invoice));
    }
    return invoice;
  }

  /**
   * Proc??dure permettant de mettre ?? jour les champs d'une facture rejet??e avec la nouvelle
   * ??criture de d??bit sur le compte 416
   *
   * @param moveLine La nouvelle ligne d'??criture de d??bit sur le compte 416
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   */
  public Invoice invoiceRejectProcess(
      MoveLine moveLine, Account doubtfulCustomerAccount, String debtPassReason) {

    Invoice invoice = moveLine.getInvoiceReject();

    invoice.setRejectMoveLine(moveLine);
    invoice.setDoubtfulCustomerOk(true);

    return invoice;
  }

  /**
   * Fonction permettant de r??cup??rer les ??critures de facture ?? transf??rer sur le compte client
   * douteux
   *
   * @param rule Le r??gle ?? appliquer :
   *     <ul>
   *       <li>0 = Cr??ance de + 6 mois
   *       <li>1 = Cr??ance de + 3 mois
   *     </ul>
   *
   * @param doubtfulCustomerAccount Le compte client douteux
   * @param company La soci??t??
   * @return Les ??critures de facture ?? transf??rer sur le compte client douteux
   */
  public List<Move> getMove(int rule, Account doubtfulCustomerAccount, Company company) {

    LocalDate date = null;

    switch (rule) {

        // Cr??ance de + 6 mois
      case 0:
        date =
            appBaseService
                .getTodayDate(company)
                .minusMonths(company.getAccountConfig().getSixMonthDebtMonthNumber());
        break;

        // Cr??ance de + 3 mois
      case 1:
        date =
            appBaseService
                .getTodayDate(company)
                .minusMonths(company.getAccountConfig().getThreeMonthDebtMontsNumber());
        break;

      default:
        break;
    }

    log.debug("Debt date taken into account : {} ", date);

    String request =
        "SELECT DISTINCT m "
            + "FROM MoveLine ml "
            + "JOIN ml.move m "
            + "JOIN ml.invoiceTermList invoiceTermList "
            + " WHERE m.company.id = "
            + company.getId()
            + " AND ml.account.useForPartnerBalance = true "
            + " AND m.functionalOriginSelect = "
            + MoveRepository.FUNCTIONAL_ORIGIN_SALE
            + " AND ml.amountRemaining > 0.00 AND ml.debit > 0.00 "
            + " AND ml.account.id != "
            + doubtfulCustomerAccount.getId()
            + " AND invoiceTermList.amountRemaining > 0.00 "
            + " AND invoiceTermList.dueDate < '"
            + date.toString()
            + "'";

    log.debug("Query : {} ", request);

    Query query = JPA.em().createQuery(request);

    @SuppressWarnings("unchecked")
    List<Move> moveList = query.getResultList();

    return moveList;
  }

  /**
   * Fonction permettant de r??cup??rer les lignes d'??criture de rejet de facture ?? transf??rer sur le
   * compte client douteux
   *
   * @param rule Le r??gle ?? appliquer :
   *     <ul>
   *       <li>0 = Cr??ance de + 6 mois
   *       <li>1 = Cr??ance de + 3 mois
   *     </ul>
   *
   * @param doubtfulCustomerAccount Le compte client douteux
   * @param company La soci??t??
   * @return Les lignes d'??criture de rejet de facture ?? transf??rer sur le comtpe client douteux
   */
  public List<? extends MoveLine> getRejectMoveLine(
      int rule, Account doubtfulCustomerAccount, Company company) {

    LocalDate date = null;
    List<? extends MoveLine> moveLineList = null;

    switch (rule) {

        // Cr??ance de + 6 mois
      case 0:
        date =
            appBaseService
                .getTodayDate(company)
                .minusMonths(company.getAccountConfig().getSixMonthDebtMonthNumber());
        moveLineList =
            moveLineRepo
                .all()
                .filter(
                    "self.move.company = ?1 AND self.account.useForPartnerBalance = 'true' "
                        + "AND self.invoiceReject IS NOT NULL AND self.amountRemaining > 0.00 AND self.debit > 0.00 AND self.dueDate < ?2 "
                        + "AND self.account != ?3 "
                        + "AND self.invoiceReject.operationTypeSelect = ?4",
                    company,
                    date,
                    doubtfulCustomerAccount,
                    InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
                .fetch();
        break;

        // Cr??ance de + 3 mois
      case 1:
        date =
            appBaseService
                .getTodayDate(company)
                .minusMonths(company.getAccountConfig().getThreeMonthDebtMontsNumber());
        moveLineList =
            moveLineRepo
                .all()
                .filter(
                    "self.move.company = ?1 AND self.account.useForPartnerBalance = 'true' "
                        + "AND self.invoiceReject IS NOT NULL AND self.amountRemaining > 0.00 AND self.debit > 0.00 AND self.dueDate < ?2 "
                        + "AND self.account != ?3 "
                        + "AND self.invoiceReject.operationTypeSelect = ?4",
                    company,
                    date,
                    doubtfulCustomerAccount,
                    InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
                .fetch();
        break;

      default:
        break;
    }

    log.debug("Debt date taken into account : {} ", date);

    return moveLineList;
  }
}
