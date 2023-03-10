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
package com.axelor.apps.account.service.bankorder.file.cfonb;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.CfonbConfig;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.PaymentSchedule;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.Reimbursement;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleLineRepository;
import com.axelor.apps.account.db.repo.ReimbursementRepository;
import com.axelor.apps.account.exception.AccountExceptionMessage;
import com.axelor.apps.account.service.PaymentScheduleService;
import com.axelor.apps.account.service.config.CfonbConfigService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.tool.StringTool;
import com.axelor.apps.tool.file.FileTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class CfonbExportService {

  protected CfonbConfig cfonbConfig;
  protected CfonbConfigService cfonbConfigService;
  protected ReimbursementRepository reimbursementRepo;
  protected PaymentScheduleLineRepository paymentScheduleLineRepo;
  protected InvoiceRepository invoiceRepo;
  protected PartnerService partnerService;
  private boolean sepa;

  @Inject
  public CfonbExportService(
      CfonbConfigService cfonbConfigService,
      ReimbursementRepository reimbursementRepo,
      PaymentScheduleLineRepository paymentScheduleLineRepo,
      InvoiceRepository invoiceRepo,
      PartnerService partnerService) {

    this.cfonbConfigService = cfonbConfigService;
    this.reimbursementRepo = reimbursementRepo;
    this.paymentScheduleLineRepo = paymentScheduleLineRepo;
    this.invoiceRepo = invoiceRepo;
    this.partnerService = partnerService;
  }

  private void init(CfonbConfig cfonbConfig) {

    this.cfonbConfig = cfonbConfig;
  }

  public void setSepa(boolean sepa) {

    this.sepa = sepa;
  }

  /**
   * ************************************** Export CFONB
   * ****************************************************
   */

  /**
   * M??thode permettant d'exporter les remboursements au format CFONB
   *
   * @param reimbursementExport
   * @param ZonedDateTime
   * @param reimbursementList
   * @throws AxelorException
   */
  public void exportCFONB(
      Company company,
      ZonedDateTime datetime,
      List<Reimbursement> reimbursementList,
      BankDetails bankDetails)
      throws AxelorException {

    this.testCompanyExportCFONBField(company);

    // param??tre obligatoire : au minimum
    //		un enregistrement emetteur par date de r??glement (code 03)
    // 		un enregistrement destinataire (code 06)
    // 		un enregistrement total (code 08)

    String senderCFONB = this.createSenderReimbursementCFONB(datetime, bankDetails);
    List<String> multiRecipientCFONB = new ArrayList<String>();
    for (Reimbursement reimbursement : reimbursementList) {
      reimbursement = reimbursementRepo.find(reimbursement.getId());

      multiRecipientCFONB.add(this.createRecipientCFONB(reimbursement));
    }
    String totalCFONB =
        this.createReimbursementTotalCFONB(
            this.getTotalAmountReimbursementExport(reimbursementList));

    //		cfonbToolService.testLength(senderCFONB, totalCFONB, multiRecipientCFONB, company);

    //		List<String> cFONB = this.createCFONBExport(senderCFONB, multiRecipientCFONB, totalCFONB);

    // Mise en majuscule des enregistrement
    //		cFONB = this.toUpperCase(cFONB);

    //		this.createCFONBFile(cFONB, datetime,
    // company.getAccountConfig().getReimbursementExportFolderPathCFONB(), "virement");
  }

  /**
   * M??thode permettant d'exporter les pr??l??vements d'??ch??ance de mensu au format CFONB
   *
   * @param paymentScheduleExport
   * @param paymentScheduleLineList
   * @param company
   * @throws AxelorException
   */
  public void exportPaymentScheduleCFONB(
      ZonedDateTime processingDateTime,
      LocalDate scheduleDate,
      List<PaymentScheduleLine> paymentScheduleLineList,
      Company company,
      BankDetails bankDetails)
      throws AxelorException {

    if (paymentScheduleLineList == null || paymentScheduleLineList.isEmpty()) {
      return;
    }

    this.testCompanyExportCFONBField(company);

    // param??tre obligatoire : au minimum
    //		un enregistrement emetteur par date de r??glement (code 03)
    // 		un enregistrement destinataire (code 06)
    // 		un enregistrement total (code 08)

    String senderCFONB = this.createSenderMonthlyExportCFONB(scheduleDate, bankDetails);
    List<String> multiRecipientCFONB = new ArrayList<String>();

    for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLineList) {
      paymentScheduleLine = paymentScheduleLineRepo.find(paymentScheduleLine.getId());
      multiRecipientCFONB.add(this.createRecipientCFONB(paymentScheduleLine, true));
    }

    String totalCFONB =
        this.createPaymentScheduleTotalCFONB(
            company, this.getTotalAmountPaymentSchedule(paymentScheduleLineList));

    //		cfonbToolService.testLength(senderCFONB, totalCFONB, multiRecipientCFONB, company);

    //		List<String> cFONB = this.createCFONBExport(senderCFONB, multiRecipientCFONB, totalCFONB);

    // Mise en majuscule des enregistrement
    //		cFONB = this.toUpperCase(cFONB);

    //		this.createCFONBFile(cFONB, processingDateTime,
    // company.getAccountConfig().getPaymentScheduleExportFolderPathCFONB(), "prelevement");
  }

  /**
   * M??thode permettant d'exporter les pr??l??vements de facture au format CFONB
   *
   * @param paymentScheduleExport
   * @param paymentScheduleLineList
   * @param invoiceList
   * @param company
   * @throws AxelorException
   */
  public void exportInvoiceCFONB(
      ZonedDateTime processingDateTime,
      LocalDate scheduleDate,
      List<Invoice> invoiceList,
      Company company,
      BankDetails bankDetails)
      throws AxelorException {

    if ((invoiceList == null || invoiceList.isEmpty())) {
      return;
    }

    this.testCompanyExportCFONBField(company);

    // param??tre obligatoire : au minimum
    //		un enregistrement emetteur par date de r??glement (code 03)
    // 		un enregistrement destinataire (code 06)
    // 		un enregistrement total (code 08)

    String senderCFONB = this.createSenderMonthlyExportCFONB(scheduleDate, bankDetails);
    List<String> multiRecipientCFONB = new ArrayList<String>();

    for (Invoice invoice : invoiceList) {
      invoice = invoiceRepo.find(invoice.getId());
      multiRecipientCFONB.add(this.createRecipientCFONB(company, invoice));
    }

    BigDecimal amount = this.getTotalAmountInvoice(invoiceList);

    String totalCFONB = this.createPaymentScheduleTotalCFONB(company, amount);

    //		cfonbToolService.testLength(senderCFONB, totalCFONB, multiRecipientCFONB, company);

    //		List<String> cFONB = this.createCFONBExport(senderCFONB, multiRecipientCFONB, totalCFONB);

    // Mise en majuscule des enregistrement
    //		cFONB = this.toUpperCase(cFONB);

    //		this.createCFONBFile(cFONB, processingDateTime,
    // company.getAccountConfig().getPaymentScheduleExportFolderPathCFONB(), "prelevement");
  }

  /**
   * M??thode permettant d'exporter les pr??l??vements de facture et d'??ch??ance de paiement au format
   * CFONB
   *
   * @param paymentScheduleExport
   * @param paymentScheduleLineList
   * @param invoiceList
   * @param company
   * @throws AxelorException
   */
  public void exportCFONB(
      ZonedDateTime processingDateTime,
      LocalDate scheduleDate,
      List<PaymentScheduleLine> paymentScheduleLineList,
      List<Invoice> invoiceList,
      Company company,
      BankDetails bankDetails)
      throws AxelorException {

    if ((paymentScheduleLineList == null || paymentScheduleLineList.isEmpty())
        && (invoiceList == null || invoiceList.isEmpty())) {
      return;
    }

    this.testCompanyExportCFONBField(company);

    // param??tre obligatoire : au minimum
    //		un enregistrement emetteur par date de r??glement (code 03)
    // 		un enregistrement destinataire (code 06)
    // 		un enregistrement total (code 08)

    String senderCFONB = this.createSenderMonthlyExportCFONB(scheduleDate, bankDetails);
    List<String> multiRecipientCFONB = new ArrayList<String>();

    // Ech??anciers
    for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLineList) {
      paymentScheduleLine = paymentScheduleLineRepo.find(paymentScheduleLine.getId());
      multiRecipientCFONB.add(this.createRecipientCFONB(paymentScheduleLine, false));
    }

    // Factures
    for (Invoice invoice : invoiceList) {
      invoice = invoiceRepo.find(invoice.getId());
      multiRecipientCFONB.add(this.createRecipientCFONB(company, invoice));
    }

    BigDecimal amount =
        this.getTotalAmountPaymentSchedule(paymentScheduleLineList)
            .add(this.getTotalAmountInvoice(invoiceList));

    String totalCFONB = this.createPaymentScheduleTotalCFONB(company, amount);

    //		cfonbToolService.testLength(senderCFONB, totalCFONB, multiRecipientCFONB, company);

    //		List<String> cFONB = this.createCFONBExport(senderCFONB, multiRecipientCFONB, totalCFONB);

    // Mise en majuscule des enregistrement
    //		cFONB = this.toUpperCase(cFONB);

    //		this.createCFONBFile(cFONB, processingDateTime,
    // company.getAccountConfig().getPaymentScheduleExportFolderPathCFONB(), "prelevement");
  }

  /**
   * Fonction permettant de cr??er un enregistrement '??metteur' pour un virement des remboursements
   *
   * @param company Une soci??t??
   * @param ZonedDateTime Une heure
   * @return Un enregistrement 'emetteur'
   * @throws AxelorException
   */
  private String createSenderReimbursementCFONB(
      ZonedDateTime zonedDateTime, BankDetails bankDetails) throws AxelorException {

    DateFormat ddmmFormat = new SimpleDateFormat("ddMM");
    String date = ddmmFormat.format(zonedDateTime.toLocalDate());
    date +=
        String.format("%s", StringTool.truncLeft(String.format("%s", zonedDateTime.getYear()), 1));

    // R??cup??ration des valeurs
    String a = this.cfonbConfig.getSenderRecordCodeExportCFONB(); // Code enregistrement
    String b1 = this.cfonbConfig.getTransferOperationCodeExportCFONB(); // Code op??ration
    String b2 = ""; // Zone r??serv??e
    String b3 = this.cfonbConfig.getSenderNumExportCFONB(); // Num??ro d'??metteur
    String c1One = ""; // Code CCD
    String c1Two = ""; // Zone r??serv??e
    String c1Three = date; // Date d'??ch??ance
    String c2 =
        this.cfonbConfig.getSenderNameCodeExportCFONB(); // Nom/Raison sociale du donneur d'ordre
    String d1One = ""; // R??f??rence de la remise
    String d1Two = ""; // Zone r??serv??e
    String d2One = ""; // Zone r??serv??e
    String d2Two = "E"; // Code monnaie
    String d2Three = ""; // Zone r??serv??e
    String d3 = bankDetails.getSortCode(); // Code guichet de la banque du donneur d'ordre
    String d4 = bankDetails.getAccountNbr(); // Num??ro de compte du donneur d???ordre
    String e = ""; // Identifiant du donneur d'ordre
    String f = ""; // Zone r??serv??e
    String g1 = bankDetails.getBankCode(); // Code ??tablissement de la banque du donneur d'ordre
    String g2 = ""; // Zone r??serv??e

    // Tronquage / remplissage ?? droite (chaine de caract??re)
    b2 = StringTool.fillStringRight(b2, ' ', 8);
    b3 = StringTool.fillStringRight(b3, ' ', 6);
    c1One = StringTool.fillStringRight(c1One, ' ', 1);
    c1Two = StringTool.fillStringRight(c1Two, ' ', 6);
    c2 = StringTool.fillStringRight(c2, ' ', 24);
    d1One = StringTool.fillStringRight(d1One, ' ', 7);
    d1Two = StringTool.fillStringRight(d1Two, ' ', 17);
    d2One = StringTool.fillStringRight(d2One, ' ', 2);
    d2Three = StringTool.fillStringRight(d2Three, ' ', 5);
    d4 = StringTool.fillStringRight(d4, ' ', 11);
    e = StringTool.fillStringRight(e, ' ', 16);
    f = StringTool.fillStringRight(f, ' ', 31);
    g2 = StringTool.fillStringRight(g2, ' ', 6);

    // Tronquage / remplissage ?? gauche (nombre)
    a = StringTool.fillStringLeft(a, '0', 2);
    b1 = StringTool.fillStringLeft(b1, '0', 2);
    c1Three = StringTool.fillStringLeft(c1Three, '0', 5);
    d3 = StringTool.fillStringLeft(d3, '0', 5);
    g1 = StringTool.fillStringLeft(g1, '0', 5);

    // V??rification AN / N / A
    //		cfonbToolService.testDigital(a, "");
    //		cfonbToolService.testDigital(b1, "");
    //		cfonbToolService.testDigital(d3, "");
    //		cfonbToolService.testDigital(g1, "");

    // cr??ation de l'enregistrement
    return a + b1 + b2 + b3 + c1One + c1Two + c1Three + c2 + d1One + d1Two + d2One + d2Two + d2Three
        + d3 + d4 + e + f + g1 + g2;
  }

  /**
   * Fonction permettant de cr??er un enregistrement '??metteur' pour un export de pr??l??vement de
   * mensu
   *
   * @param company Une soci??t??
   * @param localDate Une date
   * @return Un enregistrement 'emetteur'
   * @throws AxelorException
   */
  private String createSenderMonthlyExportCFONB(LocalDate localDate, BankDetails bankDetails)
      throws AxelorException {

    DateFormat ddmmFormat = new SimpleDateFormat("ddMM");
    String date = ddmmFormat.format(localDate.atTime(LocalTime.now()).toLocalDate());
    date += String.format("%s", StringTool.truncLeft(String.format("%s", localDate.getYear()), 1));

    // R??cup??ration des valeurs
    String a = this.cfonbConfig.getSenderRecordCodeExportCFONB(); // Code enregistrement
    String b1 = this.cfonbConfig.getDirectDebitOperationCodeExportCFONB(); // Code op??ration
    String b2 = ""; // Zone r??serv??e
    String b3 = this.cfonbConfig.getSenderNumExportCFONB(); // Num??ro d'??metteur
    String c1One = ""; // Zone r??serv??e
    String c1Two = date; // Date d'??ch??ance
    String c2 =
        this.cfonbConfig.getSenderNameCodeExportCFONB(); // Nom/Raison sociale du donneur d'ordre
    String d1One = ""; // R??f??rence de la remise
    String d1Two = ""; // Zone r??serv??e
    String d2 = ""; // Zone r??serv??e
    String d3 = bankDetails.getSortCode(); // Code guichet de la banque du donneur d'ordre
    String d4 = bankDetails.getAccountNbr(); // Num??ro de compte du donneur d???ordre
    String e = ""; // Zone r??serv??e
    String f = ""; // Zone r??serv??e
    String g1 = bankDetails.getBankCode(); // Code ??tablissement de la banque du donneur d'ordre
    String g2 = ""; // Zone r??serv??e

    // Tronquage / remplissage ?? droite (chaine de caract??re)
    b2 = StringTool.fillStringRight(b2, ' ', 8);
    b3 = StringTool.fillStringRight(b3, ' ', 6);
    c1One = StringTool.fillStringRight(c1One, ' ', 7);
    c2 = StringTool.fillStringRight(c2, ' ', 24);
    d1One = StringTool.fillStringRight(d1One, ' ', 7);
    d1Two = StringTool.fillStringRight(d1Two, ' ', 17);
    d2 = StringTool.fillStringRight(d2, ' ', 8);
    d4 = StringTool.fillStringRight(d4, ' ', 11);
    e = StringTool.fillStringRight(e, ' ', 16);
    f = StringTool.fillStringRight(f, ' ', 31);
    g2 = StringTool.fillStringRight(g2, ' ', 6);

    // Tronquage / remplissage ?? gauche (nombre)
    a = StringTool.fillStringLeft(a, '0', 2);
    b1 = StringTool.fillStringLeft(b1, '0', 2);
    c1Two = StringTool.fillStringLeft(c1Two, '0', 5);
    d3 = StringTool.fillStringLeft(d3, '0', 5);
    g1 = StringTool.fillStringLeft(g1, '0', 5);

    // V??rification AN / N / A
    //		cfonbToolService.testDigital(a, "");
    //		cfonbToolService.testDigital(b1, "");
    //		cfonbToolService.testDigital(d3, "");
    //		cfonbToolService.testDigital(g1, "");

    // cr??ation de l'enregistrement
    return a + b1 + b2 + b3 + c1One + c1Two + c2 + d1One + d1Two + d2 + d3 + d4 + e + f + g1 + g2;
  }

  /**
   * Fonction permettant de cr??er un enregistrement 'destinataire' pour un virement de remboursement
   *
   * @param company Une soci??t??
   * @param reimbursement Un remboursement
   * @return Un enregistrement 'destinataire'
   * @throws AxelorException
   */
  private String createRecipientCFONB(Reimbursement reimbursement) throws AxelorException {
    BankDetails bankDetails = reimbursement.getBankDetails();

    if (bankDetails == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          "%s :\n " + I18n.get(AccountExceptionMessage.CFONB_EXPORT_1) + " %s",
          I18n.get(BaseExceptionMessage.EXCEPTION),
          reimbursement.getRef());
    }

    BigDecimal amount = reimbursement.getAmountReimbursed();

    String ref = reimbursement.getRef(); // R??f??rence
    String partner =
        this.getPayeurPartnerName(reimbursement.getPartner()); // Nom/Raison sociale du b??n??ficiaire
    String operationCode = this.cfonbConfig.getTransferOperationCodeExportCFONB(); // Code op??ration

    return this.createRecipientCFONB(amount, ref, partner, bankDetails, operationCode);
  }

  /**
   * Fonction permettant de cr??er un enregistrement 'destinataire' pour un export de pr??l??vement
   * d'une ??ch??ance
   *
   * @param company Une soci??t??
   * @param paymentScheduleLine Une ??ch??ance
   * @return Un enregistrement 'destinataire'
   * @throws AxelorException
   */
  private String createRecipientCFONB(PaymentScheduleLine paymentScheduleLine, boolean mensu)
      throws AxelorException {
    PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();
    Partner partner = paymentSchedule.getPartner();
    BankDetails bankDetails =
        Beans.get(PaymentScheduleService.class).getBankDetails(paymentSchedule);

    BigDecimal amount = paymentScheduleLine.getDirectDebitAmount();

    String ref = paymentScheduleLine.getDebitNumber(); // R??f??rence
    String partnerName = this.getPayeurPartnerName(partner); // Nom/Raison sociale du d??biteur
    String operationCode =
        this.cfonbConfig.getDirectDebitOperationCodeExportCFONB(); // Code op??ration

    return this.createRecipientCFONB(amount, ref, partnerName, bankDetails, operationCode);
  }

  /**
   * Fonction permettant de cr??er un enregistrement 'destinataire' pour un export de pr??l??vement
   * d'une facture
   *
   * @param company Une soci??t??
   * @param moveLine L' ??criture d'export des pr??l??vement d'une facture
   * @return Un enregistrement 'destinataire'
   * @throws AxelorException
   */
  private String createRecipientCFONB(Company company, Invoice invoice) throws AxelorException {
    Partner partner = invoice.getPartner();
    BankDetails bankDetails = partnerService.getDefaultBankDetails(partner);
    if (bankDetails == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(AccountExceptionMessage.PAYMENT_SCHEDULE_2),
          I18n.get(BaseExceptionMessage.EXCEPTION),
          partner.getName());
    }

    BigDecimal amount = invoice.getDirectDebitAmount();

    String ref = invoice.getDebitNumber(); // R??f??rence
    String partnerName = this.getPayeurPartnerName(partner); // Nom/Raison sociale du d??biteur
    String operationCode =
        this.cfonbConfig.getDirectDebitOperationCodeExportCFONB(); // Code op??ration

    return this.createRecipientCFONB(amount, ref, partnerName, bankDetails, operationCode);
  }

  /**
   * Fonction permettant de cr??er un enregistrement 'destinataire'
   *
   * @param company Une soci??t??
   * @param amount Le montant de l'enregistrement
   * @param ref Une r??f??rence de pr??l??vement
   * @param label Un libell??
   * @param partner Un tiers payeur
   * @param bankDetails Un RIB
   * @param operationCode Le code d'op??ration d??fini par soci??t??
   * @return L'enregistrement 'destinataire'
   * @throws AxelorException
   */
  private String createRecipientCFONB(
      BigDecimal amount, String ref, String partner, BankDetails bankDetails, String operationCode)
      throws AxelorException {
    this.testBankDetailsField(bankDetails);

    String amountFixed = amount.setScale(2).toString().replace(".", "");

    // R??cup??ration des valeurs
    String a = this.cfonbConfig.getRecipientRecordCodeExportCFONB(); // Code enregistrement
    String b1 = operationCode; // Code op??ration
    String b2 = ""; // Zone r??serv??e
    String b3 = this.cfonbConfig.getSenderNumExportCFONB(); // Num??ro d'??metteur
    String c1 = ref; // R??f??rence
    String c2 = partner; // Nom/Raison sociale du b??n??ficiaire
    String d1 = bankDetails.getBankAddress().getAddress(); // Domiciliation
    String d2 = ""; // D??claration de la balance des paiement
    String d3 =
        bankDetails.getSortCode(); // Code guichet de la banque du donneur d'ordre / du d??biteur
    String d4 = bankDetails.getAccountNbr(); // Num??ro de compte du b??n??ficiaire / du d??biteur
    String e = amountFixed; // Montant du virement
    String f = ref; // Libell??
    String g1 =
        bankDetails
            .getBankCode(); // Code ??tablissement de la banque du donneur d'ordre / du d??biteur
    String g2 = ""; // Zone r??serv??e

    // Tronquage / remplissage ?? droite (chaine de caract??re)
    b2 = StringTool.fillStringRight(b2, ' ', 8);
    b3 = StringTool.fillStringRight(b3, ' ', 6);
    c1 = StringTool.fillStringRight(c1, ' ', 12);
    c2 = StringTool.fillStringRight(c2, ' ', 24);
    d1 = StringTool.fillStringRight(d1, ' ', 24);
    d2 = StringTool.fillStringRight(d2, ' ', 8);
    d4 = StringTool.fillStringRight(d4, ' ', 11);
    f = StringTool.fillStringRight(f, ' ', 31);
    g2 = StringTool.fillStringRight(g2, ' ', 6);

    // Tronquage / remplissage ?? gauche (nombre)
    a = StringTool.fillStringLeft(a, '0', 2);
    b1 = StringTool.fillStringLeft(b1, '0', 2);
    d3 = StringTool.fillStringLeft(d3, '0', 5);
    e = StringTool.fillStringLeft(e, '0', 16);
    g1 = StringTool.fillStringLeft(g1, '0', 5);

    return a + b1 + b2 + b3 + c1 + c2 + d1 + d2 + d3 + d4 + e + f + g1 + g2;
  }

  /**
   * Fonction permettant de cr??er un enregistrement 'total' au format CFONB pour un remboursement
   *
   * @param company Une soci??t??
   * @param amount Le montant total des enregistrements 'destinataire'
   * @return
   */
  private String createReimbursementTotalCFONB(BigDecimal amount) {

    // Code op??ration
    String operationCode = this.cfonbConfig.getTransferOperationCodeExportCFONB();

    return this.createTotalCFONB(amount, operationCode);
  }

  /**
   * Fonction permettant de cr??er un enregistrement 'total' au format CFONB pour un ??ch??ancier
   *
   * @param company Une soci??t??
   * @param amount Le montant total des enregistrements 'destinataire'
   * @return L'enregistrement 'total'
   */
  private String createPaymentScheduleTotalCFONB(Company company, BigDecimal amount) {

    // Code op??ration
    String operationCode = this.cfonbConfig.getDirectDebitOperationCodeExportCFONB();

    return this.createTotalCFONB(amount, operationCode);
  }

  /**
   * Fonction permettant de cr??er un enregistrement 'total' au format CFONB
   *
   * @param company Une soci??t??
   * @param amount Le montant total des enregistrements 'destinataire'
   * @param operationCode Le type d'op??ration :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Pr??l??vement
   *     </ul>
   *
   * @return L'enregistrement 'total'
   */
  private String createTotalCFONB(BigDecimal amount, String operationCode) {
    String totalAmount = amount.setScale(2).toString().replace(".", "");

    // R??cup??ration des valeurs
    String a = this.cfonbConfig.getTotalRecordCodeExportCFONB(); // Code enregistrement
    String b1 = operationCode; // Code op??ration
    String b2 = ""; // Zone r??serv??e
    String b3 = this.cfonbConfig.getSenderNumExportCFONB(); // Num??ro d'??metteur
    String c1 = ""; // Zone r??serv??e
    String c2 = ""; // Zone r??serv??e
    String d1 = ""; // Zone r??serv??e
    String d2 = ""; // Zone r??serv??e
    String d3 = ""; // Zone r??serv??e
    String d4 = ""; // Zone r??serv??e
    String e = totalAmount; // Montant de la remise
    String f = ""; // Zone r??serv??e
    String g1 = ""; // Zone r??serv??e
    String g2 = ""; // Zone r??serv??e

    // Tronquage / remplissage ?? droite (chaine de caract??re)
    b2 = StringTool.fillStringRight(b2, ' ', 8);
    b3 = StringTool.fillStringRight(b3, ' ', 6);
    c1 = StringTool.fillStringRight(c1, ' ', 12);
    c2 = StringTool.fillStringRight(c2, ' ', 24);
    d1 = StringTool.fillStringRight(d1, ' ', 24);
    d2 = StringTool.fillStringRight(d2, ' ', 8);
    d3 = StringTool.fillStringRight(d3, ' ', 5);
    d4 = StringTool.fillStringRight(d4, ' ', 11);
    f = StringTool.fillStringRight(f, ' ', 31);
    g1 = StringTool.fillStringRight(g1, ' ', 5);
    g2 = StringTool.fillStringRight(g2, ' ', 6);

    // Tronquage / remplissage ?? gauche (nombre)
    a = StringTool.fillStringLeft(a, '0', 2);
    b1 = StringTool.fillStringLeft(b1, '0', 2);
    e = StringTool.fillStringLeft(e, '0', 16);

    return a + b1 + b2 + b3 + c1 + c2 + d1 + d2 + d3 + d4 + e + f + g1 + g2;
  }

  /**
   * Proc??dure permettant de cr??er un fichier CFONB au format .dat
   *
   * @param cFONB Le contenu du fichier, des enregistrements CFONB
   * @param ZonedDateTime La date permettant de d??terminer le nom du fichier cr????
   * @param destinationFolder Le r??pertoire de destination
   * @param prefix Le pr??fix utilis??
   * @throws AxelorException
   */
  private void createCFONBFile(
      List<String> cFONB, ZonedDateTime zonedDateTime, String destinationFolder, String prefix)
      throws AxelorException {
    DateFormat yyyyMMddHHmmssFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    String dateFileName = yyyyMMddHHmmssFormat.format(zonedDateTime);
    String fileName = String.format("%s%s.dat", prefix, dateFileName);

    try {
      FileTool.writer(destinationFolder, fileName, cFONB);
    } catch (IOException e) {
      throw new AxelorException(
          e.getCause(),
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(AccountExceptionMessage.CFONB_EXPORT_2),
          I18n.get(BaseExceptionMessage.EXCEPTION),
          e);
    }
  }

  /**
   * M??thode permettant de construire le Nom/Raison sociale du tiers payeur d'un m??moire
   *
   * @param memory Un m??moire
   * @return Civilit?? + Nom + Pr??nom si c'est une personne physique Civilit?? + Nom sinon
   */
  private String getPayeurPartnerName(Partner partner) {

    if (partner.getTitleSelect() != null) {
      return String.format("%s %s", partner.getTitleSelect(), partner.getName());
    } else {
      return String.format("%s", partner.getName());
    }
  }

  /**
   * M??thode permettant de calculer le montant total des remboursements
   *
   * @param reimbursementList Une liste de remboursement
   * @return Le montant total
   */
  private BigDecimal getTotalAmountReimbursementExport(List<Reimbursement> reimbursementList) {
    BigDecimal totalAmount = BigDecimal.ZERO;
    for (Reimbursement reimbursement : reimbursementList) {
      reimbursement = reimbursementRepo.find(reimbursement.getId());
      totalAmount = totalAmount.add(reimbursement.getAmountReimbursed());
    }
    return totalAmount;
  }

  /**
   * Fonction permettant de r??cup??rer le montant total ?? pr??lever d'une liste d'??ch??ance de mensu
   *
   * @param paymentScheduleLineList Une liste d'??ch??ance de mensu
   * @return Le montant total ?? pr??lever
   */
  private BigDecimal getTotalAmountPaymentSchedule(
      List<PaymentScheduleLine> paymentScheduleLineList) {
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLineList) {

      totalAmount =
          totalAmount.add(
              paymentScheduleLineRepo.find(paymentScheduleLine.getId()).getDirectDebitAmount());
    }
    return totalAmount;
  }

  /**
   * Fonction permettant de r??cup??rer le montant total ?? pr??lever d'une liste d'??ch??ance de mensu
   *
   * @param paymentScheduleLineList Une liste d'??ch??ance de mensu
   * @return Le montant total ?? pr??lever
   */
  private BigDecimal getTotalAmountInvoice(List<Invoice> invoiceList) {
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (Invoice invoice : invoiceList) {

      totalAmount = totalAmount.add(invoiceRepo.find(invoice.getId()).getDirectDebitAmount());
    }

    return totalAmount;
  }

  /**
   * Proc??dure permettant de v??rifier la conformit?? des champs en rapport avec les exports CFONB
   * d'une soci??t??
   *
   * @param company La soci??t??
   * @throws AxelorException
   */
  public void testCompanyExportCFONBField(Company company) throws AxelorException {

    AccountConfig accountConfig = cfonbConfigService.getAccountConfig(company);

    cfonbConfigService.getReimbursementExportFolderPathCFONB(accountConfig);
    cfonbConfigService.getPaymentScheduleExportFolderPathCFONB(accountConfig);

    this.init(cfonbConfigService.getCfonbConfig(company));

    cfonbConfigService.getSenderRecordCodeExportCFONB(this.cfonbConfig);
    cfonbConfigService.getSenderNumExportCFONB(this.cfonbConfig);
    cfonbConfigService.getSenderNameCodeExportCFONB(this.cfonbConfig);
    cfonbConfigService.getRecipientRecordCodeExportCFONB(this.cfonbConfig);
    cfonbConfigService.getTotalRecordCodeExportCFONB(this.cfonbConfig);
    cfonbConfigService.getTransferOperationCodeExportCFONB(this.cfonbConfig);
    cfonbConfigService.getDirectDebitOperationCodeExportCFONB(this.cfonbConfig);
  }

  /**
   * Proc??dure permettant de v??rifier la conformit?? des champs d'un RIB
   *
   * @param bankDetails Le RIB
   * @throws AxelorException
   */
  public void testBankDetailsField(BankDetails bankDetails) throws AxelorException {
    if (bankDetails.getSortCode() == null || bankDetails.getSortCode().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(AccountExceptionMessage.CFONB_EXPORT_3),
          I18n.get(BaseExceptionMessage.EXCEPTION),
          bankDetails.getIban(),
          bankDetails.getPartner().getName());
    }
    if (bankDetails.getAccountNbr() == null || bankDetails.getAccountNbr().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(AccountExceptionMessage.CFONB_EXPORT_4),
          I18n.get(BaseExceptionMessage.EXCEPTION),
          bankDetails.getIban(),
          bankDetails.getPartner().getName());
    }
    if (bankDetails.getBankCode() == null || bankDetails.getBankCode().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(AccountExceptionMessage.CFONB_EXPORT_5),
          I18n.get(BaseExceptionMessage.EXCEPTION),
          bankDetails.getIban(),
          bankDetails.getPartner().getName());
    }
    String bankAddress = bankDetails.getBankAddress().getAddress();
    if (bankAddress == null || bankAddress.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(AccountExceptionMessage.CFONB_EXPORT_6),
          I18n.get(BaseExceptionMessage.EXCEPTION),
          bankDetails.getIban(),
          bankDetails.getPartner().getName());
    }
  }
}
