package com.bank.aml.seed;

import com.bank.aml.config.AppProperties;
import com.bank.aml.domain.AlertEntity;
import com.bank.aml.domain.CaseEntity;
import com.bank.aml.domain.CustomerEntity;
import com.bank.aml.domain.TransactionEntity;
import com.bank.aml.repo.AlertRepository;
import com.bank.aml.repo.CaseRepository;
import com.bank.aml.repo.CustomerRepository;
import com.bank.aml.repo.TransactionRepository;
import com.bank.aml.service.CaseConsolidationService;
import com.bank.aml.service.SanctionsScreeningService;
import com.bank.aml.util.Jsons;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class SeedDataRunner implements ApplicationRunner {
    /** Anchored to the live calendar so the data set never drifts away from demo day. */
    public static final LocalDate DEMO_DAY = LocalDate.now(ZoneOffset.UTC);
    public static final Instant DEMO_NOW = DEMO_DAY.atTime(10, 0).toInstant(ZoneOffset.UTC);

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final CaseRepository caseRepository;
    private final AlertRepository alertRepository;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final CaseConsolidationService caseConsolidationService;
    private final AppProperties appProperties;

    private static final long RANDOM_SEED = 42L;

    private final AtomicInteger txnSeq = new AtomicInteger(1000);
    private final Random random = new Random(RANDOM_SEED);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sanctionsScreeningService.ensureV1Loaded();
        if (customerRepository.count() > 0) {
            log.info("Seed skipped — customers already present");
            return;
        }
        seedAll();
    }

    /** Builds the whole demo data set. Safe to call again after {@code DemoResetService} truncates. */
    @Transactional
    public Map<String, Long> seedAll() {
        log.info("Seeding AML demo data for {}", DEMO_DAY);
        // Both generators must be rewound, not just the reference counter: otherwise every reset
        // produces a different baseline, and with it a different ACME score.
        random.setSeed(RANDOM_SEED);
        txnSeq.set(1000);
        List<CustomerEntity> customers = seedCustomers();
        seedBaselineTransactions(customers);
        CustomerEntity acme = customers.get(0);
        seedAcmeStory(acme);
        // Every case below — including ACME's — is produced by the rule engine, never hand-written.
        caseConsolidationService.evaluateAndConsolidate(
                acme.getId(), DEMO_DAY.minusDays(1).atTime(18, 30).toInstant(ZoneOffset.UTC));
        assignAndSetSla(acme, 8);
        seedExtraCases(customers);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("customers", customerRepository.count());
        counts.put("transactions", transactionRepository.count());
        counts.put("cases", caseRepository.count());
        counts.put("alerts", alertRepository.count());
        log.info("Seed complete: {}", counts);
        return counts;
    }

    private List<CustomerEntity> seedCustomers() {
        String[] industries = {"Trading", "Logistics", "Consultancy"};
        String[] forms = {"Ltd", "PLC"};
        String[] crrs = {"LOW", "LOW", "MEDIUM", "MEDIUM", "HIGH"};
        List<CustomerEntity> list = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            CustomerEntity c = new CustomerEntity();
            c.setCustomerRef(String.format("CUS-%06d", i));
            if (i == 1) {
                c.setName("ACME Trading Ltd");
                c.setIndustry("Trading");
                c.setLegalForm("Ltd");
                c.setCrr("MEDIUM");
            } else if (i == 50) {
                c.setName("Northern Peak Holdings Ltd");
                c.setIndustry("Consultancy");
                c.setLegalForm("Ltd");
                c.setCrr("LOW");
            } else {
                c.setName(companyName(i));
                c.setIndustry(industries[i % industries.length]);
                c.setLegalForm(forms[i % forms.length]);
                c.setCrr(crrs[i % crrs.length]);
            }
            c.setSegment("CORPORATE");
            c.setIncorporationCountry(i % 7 == 0 ? "IE" : "GB");
            c.setRegistrationDate(LocalDate.of(2010 + (i % 14), 1 + (i % 12), 1 + (i % 27)));
            c.setMonitoringStatus("NORMAL");
            c.setCreatedAt(DEMO_NOW.minus(400, ChronoUnit.DAYS));
            list.add(customerRepository.save(c));
        }
        return list;
    }

    private String companyName(int i) {
        String[] a = {"Bright", "Silver", "Harbour", "Atlas", "Summit", "Pacific", "Crown", "Vertex", "Northgate", "Oak"};
        String[] b = {"Goods", "Freight", "Partners", "Solutions", "Commerce", "Markets", "Advisory", "Exports"};
        return a[i % a.length] + " " + b[(i * 3) % b.length] + " " + (i % 2 == 0 ? "Ltd" : "PLC");
    }

    private void seedBaselineTransactions(List<CustomerEntity> customers) {
        for (CustomerEntity c : customers) {
            if (c.getCustomerRef().equals("CUS-000050")) {
                // dormant — almost no recent activity
                saveTxn(c, "INBOUND", bd(2500), "GBP", "CP-OLD-1", "Legacy Supplier", "DE",
                        DEMO_DAY.minusDays(120).atTime(11, 0).toInstant(ZoneOffset.UTC));
                continue;
            }
            int n = 25 + random.nextInt(20);
            for (int i = 0; i < n; i++) {
                int dayOffset = 2 + random.nextInt(58);
                Instant when = DEMO_DAY.minusDays(dayOffset).atTime(9 + random.nextInt(8), random.nextInt(60)).toInstant(ZoneOffset.UTC);
                boolean inbound = random.nextBoolean();
                double amt = Math.exp(8 + random.nextGaussian() * 0.6); // ~3k-10k typical
                if (c.getCustomerRef().equals("CUS-000001")) {
                    // ACME baseline daily inbound around ~10k median
                    amt = 8000 + random.nextDouble() * 4000;
                    inbound = random.nextDouble() < 0.55;
                }
                String ccy = switch (random.nextInt(3)) { case 0 -> "GBP"; case 1 -> "EUR"; default -> "USD"; };
                String cp = "CP-" + (1000 + random.nextInt(80));
                String country = switch (random.nextInt(5)) { case 0 -> "DE"; case 1 -> "FR"; case 2 -> "NL"; case 3 -> "US"; default -> "SG"; };
                saveTxn(c, inbound ? "INBOUND" : "OUTBOUND", bd(amt), ccy, cp, "Counterparty " + cp, country, when);
            }
        }
    }

    private void seedAcmeStory(CustomerEntity acme) {
        // The three payers below are long-standing trade counterparties, so give them real history.
        // Without it the rule engine would correctly, but misleadingly, flag them as brand new.
        String[][] established = {
            {"CP-LEGACY-A", "Rhine Distributors GmbH", "DE"},
            {"CP-LEGACY-B", "Seine Trade SA", "FR"},
            {"CP-LEGACY-C", "Baltic Merchants OU", "EE"},
        };
        for (int d = 14; d <= 70; d += 14) {
            for (String[] cp : established) {
                saveTxn(acme, "INBOUND", bd(9000 + random.nextInt(2500)), "GBP", cp[0], cp[1], cp[2],
                        DEMO_DAY.minusDays(d).atTime(10, 20).toInstant(ZoneOffset.UTC));
            }
        }

        Instant t0 = DEMO_DAY.minusDays(1).atTime(16, 10).toInstant(ZoneOffset.UTC);
        // Three credits from the established payers, £84,200 in 15 minutes.
        saveTxn(acme, "INBOUND", bd(30000), "GBP", "CP-LEGACY-A", "Rhine Distributors GmbH", "DE", t0);
        saveTxn(acme, "INBOUND", bd(27200), "GBP", "CP-LEGACY-B", "Seine Trade SA", "FR", t0.plus(8, ChronoUnit.MINUTES));
        saveTxn(acme, "INBOUND", bd(27000), "GBP", "CP-LEGACY-C", "Baltic Merchants OU", "EE", t0.plus(15, ChronoUnit.MINUTES));

        Instant outStart = t0.plus(20, ChronoUnit.MINUTES);
        // 92% of £84,200 pushed out to five beneficiaries in 35 minutes: four never seen before,
        // and the single largest leg goes to a higher-risk jurisdiction.
        saveTxn(acme, "OUTBOUND", bd(16000), "GBP", "CP-NEW-01", "Cedar Ridge LLC", "US", outStart);
        saveTxn(acme, "OUTBOUND", bd(14500), "GBP", "CP-NEW-02", "Orion Payments Ltd", "CY", outStart.plus(7, ChronoUnit.MINUTES));
        saveTxn(acme, "OUTBOUND", bd(12000), "GBP", "CP-NEW-03", "Horizon FX Desk", "LU", outStart.plus(14, ChronoUnit.MINUTES));
        saveTxn(acme, "OUTBOUND", bd(26000), "GBP", "CP-NEW-04", "Sahel Logistics NG", "NG", outStart.plus(22, ChronoUnit.MINUTES));
        saveTxn(acme, "OUTBOUND", bd(8964), "GBP", "CP-LEGACY-A", "Rhine Distributors GmbH", "DE", outStart.plus(35, ChronoUnit.MINUTES));
    }

    /**
     * Never touches the score. The priority score is produced by the rule engine and must always
     * equal the sum of its alerts, otherwise the "every point is traceable" claim is false.
     */
    private void assignAndSetSla(CustomerEntity customer, long slaHours) {
        caseRepository.findByCustomerId(customer.getId()).stream()
                .filter(c -> "OPEN".equals(c.getStatus()))
                .findFirst()
                .ifPresent(open -> {
                    open.setAssignedTo(appProperties.getActor());
                    // SLA is measured against the live clock so the queue stays believable all week.
                    open.setSlaDueAt(slaHours >= 0
                            ? Instant.now().plus(slaHours, ChronoUnit.HOURS)
                            : Instant.now().minus(-slaHours, ChronoUnit.HOURS));
                    caseRepository.save(open);
                });
    }

    /** Behaviour patterns that trigger different subsets of R1-R4, so alert counts vary naturally. */
    private enum Profile { FULL_DISPERSAL, DISPERSAL_NO_HIGH_RISK, DISPERSAL_ONLY, AMOUNT_ONLY, NEW_BENEFICIARIES }

    private void seedExtraCases(List<CustomerEntity> customers) {
        // customerIndex -> behaviour. Scores are NEVER set here; whatever the rules produce is the truth.
        int[] idxs =        {2, 5, 8, 12, 18, 22, 27, 31, 35, 38, 41, 44, 46, 48};
        Profile[] profiles = {
            Profile.FULL_DISPERSAL, Profile.DISPERSAL_NO_HIGH_RISK, Profile.AMOUNT_ONLY,
            Profile.FULL_DISPERSAL, Profile.NEW_BENEFICIARIES, Profile.DISPERSAL_NO_HIGH_RISK,
            Profile.DISPERSAL_ONLY, Profile.AMOUNT_ONLY, Profile.NEW_BENEFICIARIES,
            Profile.DISPERSAL_ONLY, Profile.FULL_DISPERSAL, Profile.AMOUNT_ONLY,
            Profile.NEW_BENEFICIARIES, Profile.DISPERSAL_ONLY
        };
        for (int i = 0; i < idxs.length; i++) {
            CustomerEntity c = customers.get(idxs[i]);
            Instant asOf = DEMO_DAY.minusDays(1).atTime(8 + (i % 10), 15).toInstant(ZoneOffset.UTC);
            injectBehaviour(c, profiles[i], asOf.minus(2, ChronoUnit.HOURS), i);
            caseConsolidationService.evaluateAndConsolidate(c.getId(), asOf);
            // first two are past their SLA against the live clock, the rest are still in time
            assignAndSetSla(c, i < 2 ? -(2 + i) : (6 + i * 2));
        }

        // A closed case from three weeks ago, so prior_recent_cases is not always zero.
        // It goes through the rule engine like every other case — its score is earned, not typed in.
        CustomerEntity closedCust = customers.get(30);
        Instant closedAsOf = DEMO_NOW.minus(20, ChronoUnit.DAYS);
        injectBehaviour(closedCust, Profile.DISPERSAL_NO_HIGH_RISK, closedAsOf.minus(2, ChronoUnit.HOURS), 99);
        caseConsolidationService.evaluateAndConsolidate(closedCust.getId(), closedAsOf)
                .ifPresent(closed -> {
                    closed.setStatus("CLOSED_NFA");
                    closed.setAssignedTo(appProperties.getActor());
                    closed.setOpenedAt(closedAsOf);
                    closed.setSlaDueAt(closedAsOf.plus(24, ChronoUnit.HOURS));
                    closed.setDisposedAt(closedAsOf.plus(2, ChronoUnit.DAYS));
                    closed.setDisposedBy(appProperties.getActor());
                    closed.setDispositionReason("Activity explained by seasonal inventory restocking; "
                            + "invoices and shipping documents obtained from the relationship manager.");
                    caseRepository.save(closed);
                });
    }

    /**
     * Writes transactions only. Every alert and every point is then derived by the rule engine,
     * so nothing on screen is fabricated.
     */
    private void injectBehaviour(CustomerEntity c, Profile profile, Instant t0, int salt) {
        String tag = "S" + salt;
        switch (profile) {
            // Large inflow, dispersed fast to five new beneficiaries, one higher-risk -> R1 R2 R3 R4
            case FULL_DISPERSAL -> {
                saveTxn(c, "INBOUND", bd(17000 + salt * 400L), "GBP", "CP-" + tag + "-IN", "Meridian Supply Co", "DE", t0);
                saveTxn(c, "OUTBOUND", bd(4300), "GBP", "CP-" + tag + "-1", "Kestrel Partners", "US", t0.plus(12, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(3800), "GBP", "CP-" + tag + "-2", "Lumen Trade BV", "NL", t0.plus(21, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(3400), "GBP", "CP-" + tag + "-3", "Delta Consulting", "CY", t0.plus(29, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(2600), "GBP", "CP-" + tag + "-4", "Sahara Freight", "NG", t0.plus(40, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(1900), "GBP", "CP-" + tag + "-5", "Vale Holdings", "IE", t0.plus(52, ChronoUnit.MINUTES));
            }
            // Same shape but every beneficiary is in a low-risk country -> R1 R2 R3
            case DISPERSAL_NO_HIGH_RISK -> {
                saveTxn(c, "INBOUND", bd(28000 + salt * 600L), "GBP", "CP-" + tag + "-IN", "Halden Group", "SE", t0);
                saveTxn(c, "OUTBOUND", bd(9200), "GBP", "CP-" + tag + "-1", "Fjord Logistics", "NO", t0.plus(18, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(8100), "GBP", "CP-" + tag + "-2", "Alpine Components", "CH", t0.plus(31, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(6900), "GBP", "CP-" + tag + "-3", "Bruges Textiles", "BE", t0.plus(44, ChronoUnit.MINUTES));
            }
            // Inflow within the customer's normal range, but dispersed immediately -> R2 R3
            case DISPERSAL_ONLY -> {
                saveTxn(c, "INBOUND", bd(4800), "GBP", "CP-" + tag + "-IN", "Routine Payer Ltd", "FR", t0);
                saveTxn(c, "OUTBOUND", bd(1700), "GBP", "CP-" + tag + "-1", "Quill Services", "ES", t0.plus(15, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(1500), "GBP", "CP-" + tag + "-2", "Orchard Media", "PT", t0.plus(26, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(1300), "GBP", "CP-" + tag + "-3", "Bay Analytics", "IT", t0.plus(38, ChronoUnit.MINUTES));
            }
            // A single unusually large credit that simply sits there -> R1 only
            case AMOUNT_ONLY ->
                saveTxn(c, "INBOUND", bd(36000 + salt * 700L), "GBP", "CP-" + tag + "-IN", "Crestline Capital", "LU", t0);
            // Ordinary sums, but paid to beneficiaries never seen before -> R3 only
            case NEW_BENEFICIARIES -> {
                saveTxn(c, "OUTBOUND", bd(2600), "GBP", "CP-" + tag + "-1", "Novus Print Ltd", "GB", t0.plus(30, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(2100), "GBP", "CP-" + tag + "-2", "Ridge Facilities", "GB", t0.plus(95, ChronoUnit.MINUTES));
                saveTxn(c, "OUTBOUND", bd(1900), "GBP", "CP-" + tag + "-3", "Talbot Legal LLP", "GB", t0.plus(160, ChronoUnit.MINUTES));
            }
        }
    }

    private TransactionEntity saveTxn(
            CustomerEntity c, String direction, BigDecimal amount, String ccy,
            String cpRef, String cpName, String cpCountry, Instant when) {
        BigDecimal rate = switch (ccy) {
            case "EUR" -> new BigDecimal("0.86000000");
            case "USD" -> new BigDecimal("0.79000000");
            default -> BigDecimal.ONE;
        };
        BigDecimal gbp = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        TransactionEntity t = new TransactionEntity();
        t.setTxnRef(String.format("TXN-%06d", txnSeq.getAndIncrement()));
        t.setCustomerId(c.getId());
        t.setAccountRef("ACC-" + c.getCustomerRef());
        t.setDirection(direction);
        t.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        t.setCurrency(ccy);
        t.setAmountGbp(gbp);
        t.setFxRateUsed(rate);
        t.setFxRateDate(LocalDate.ofInstant(when, ZoneOffset.UTC));
        t.setCounterpartyRef(cpRef);
        t.setCounterpartyName(cpName);
        t.setCounterpartyCountry(cpCountry);
        t.setExecutedAt(when);
        t.setStatus("RELEASED");
        t.setCreatedAt(when);
        return transactionRepository.save(t);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
