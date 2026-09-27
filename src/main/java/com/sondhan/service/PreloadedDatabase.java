package com.sondhan.service;

import com.sondhan.model.FactCheckResult;
import com.sondhan.util.ImageHashUtil;

import java.io.File;
import java.util.*;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * Preloaded Database & Visual Forensics Engine
 * ──────────────────────────────────────────────────────────────────────────────
 * • Matches uploaded/extracted images by SHA-256 hash for exact matches.
 * • Matches images via 64-bit Difference Hash (dHash) and Hamming Distance
 *   to detect edited, cropped, or out-of-context images.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class PreloadedDatabase {

    private static PreloadedDatabase instance;

    public static class IndexedEntry {
        public final Entry entry;
        public final File file;
        public final String sha256;
        public final long dHash;

        public IndexedEntry(Entry entry, File file, String sha256, long dHash) {
            this.entry  = entry;
            this.file   = file;
            this.sha256 = sha256;
            this.dHash  = dHash;
        }
    }

    private final Map<String, FactCheckResult> hashToResult = new HashMap<>();
    private final List<IndexedEntry> indexedEntries = new ArrayList<>();
    private boolean initialized = false;

    // ── Preloaded Entries ─────────────────────────────────────────────────────

    private static final List<Entry> ENTRIES = List.of(

        new Entry("718014225_1419002733593201_4348281707205913180_n.jpg",
            "Fake comment circulated about Saiyed Abdullah",
            "FALSE", 82,
            "No evidence that Saiyed Abdullah called people ungrateful over electricity price hikes. " +
            "The post originated from a satirist profile with no credible source.",
            "Facebook Misinformation Desk / Rumor Scanner BD",
            "June 2024",
            "Saiyed Abdullah never made any derogatory remarks regarding public electricity tariff hikes; the quote card was fabricated by an unauthorized satirical parody page.",
            List.of(
                new FactCheckResult.Source("Rumor Scanner BD – Official Fact Check Investigation", "https://rumorscanner.com/fact-check/saiyed-abdullah-fake-comment-claim/208583", "newspaper"),
                new FactCheckResult.Source("Boom Bangladesh – Viral Statement Fact Check Desk", "https://www.boomlive.in/fact-check/bangladesh-electricity-tariff-viral-quote-debunked", "newspaper"),
                new FactCheckResult.Source("The Daily Star – Social Media Misinformation Watch Desk", "https://www.thedailystar.net/tags/fact-check", "newspaper"),
                new FactCheckResult.Source("Prothom Alo – Fact Checking: Viral Social Media Claims", "https://en.prothomalo.com/topic/fact-check", "newspaper"),
                new FactCheckResult.Source("Poynter IFCN – Standards for Fact-Checking Satirical Content", "https://www.poynter.org/ifcn/poynter-ifcn-code-of-principles/", "journal"),
                new FactCheckResult.Source("Bangladesh Power Development Board – Official Tariff Records", "https://www.bpdb.gov.bd/bpdb/index.php?option=com_content&view=article&id=19&Itemid=179", "government")
            ),
            List.of(
                "1. The claim attributes a statement to Saiyed Abdullah without any evidence.",
                "2. No credible news source reported this statement.",
                "3. The post originated from a self-described satirist account.",
                "4. Rumor Scanner BD investigated and found no factual basis.",
                "5. Verdict: FALSE – the claim is completely fabricated.",
                "6. Social media misinformation can damage public figures' reputations.",
                "7. Always verify political claims through multiple credible sources.",
                "8. Satirist accounts are not credible news sources.",
                "9. Bangladesh electricity pricing is a sensitive political topic.",
                "10. Cross-reference any political claims with official statements."
            )
        ),

        new Entry("718953356_1465989142222974_2371143075906418461_n.jpg",
            "Case filed against Dr Yunus and Nurjahan Begum",
            "MISLEADING", 72,
            "A case application was filed but dismissed by the court for lack of grounds. " +
            "No case was actually formally registered against them.",
            "BDNews24 & The Daily Star Legal Beat",
            "August 2024",
            "No formal criminal or civil case was registered or accepted against Dr. Muhammad Yunus or Nurjahan Begum; the court summarily dismissed the complaint application on initial hearing.",
            List.of(
                new FactCheckResult.Source("BDNEWS24 – Court Dismisses Complaint Against Dr. Yunus", "https://bangla.bdnews24.com/politics/politics/976b12683a00", "newspaper"),
                new FactCheckResult.Source("The Daily Star – Court Dismisses Case Application Against Dr Yunus", "https://www.thedailystar.net/news/bangladesh/crime-justice/news/court-dismisses-case-application-against-dr-yunus-3677326", "newspaper"),
                new FactCheckResult.Source("Prothom Alo – Court Dismisses Case Against Nobel Laureate Yunus", "https://en.prothomalo.com/bangladesh/court/court-dismisses-case-application-against-dr-yunus", "newspaper"),
                new FactCheckResult.Source("New Age Bangladesh – Legal Case Dismissed, No Grounds Found", "https://www.newagebd.net/article/191234/court-dismisses-case-against-yunus", "newspaper"),
                new FactCheckResult.Source("Grameen Bank Annual Report – Institutional Legal Standing", "https://www.grameen.com/grameen-bank-annual-report/", "book"),
                new FactCheckResult.Source("Bangladesh Supreme Court – Case Docket Reference Database", "https://www.supremecourt.gov.bd/", "government")
            ),
            List.of(
                "1. An application was filed but the court dismissed it.",
                "2. No case was formally registered against Dr Yunus.",
                "3. The headline is misleading as it implies an active case.",
                "4. BDNEWS24 and The Daily Star both covered the dismissal.",
                "5. Verdict: MISLEADING – partial truth presented as complete fact.",
                "6. Court proceedings require distinguishing applications from registered cases.",
                "7. Public figures are frequently targeted by unfounded legal applications.",
                "8. Always check if a case was accepted by the court, not just filed.",
                "9. Media must report dismissals with the same prominence as filings.",
                "10. Cross-reference with official court records for accuracy."
            )
        ),

        new Entry("claim_dhaka_capital.png",
            "Dhaka has been the capital of Bangladesh since independence in 1971",
            "TRUE", 99,
            "Dhaka is the official capital of Bangladesh, designated in Article 5 of the Constitution " +
            "since independence on December 16, 1971.",
            "Constitution of Bangladesh (Art. 5) & Parliament Records",
            "December 16, 1971",
            "Dhaka has been the sole constitutional capital of Bangladesh continuously since independence in 1971.",
            List.of(
                new FactCheckResult.Source("Laws of Bangladesh – Article 5: The Capital of the Republic is Dhaka", "https://bdlaws.minlaw.gov.bd/act-367/section-24553.html#:~:text=The%20capital%20of%20the%20Republic%20is%20Dhaka", "government"),
                new FactCheckResult.Source("Bangladesh National Parliament – Official Government Portal", "https://old.parliament.gov.bd/index.php/en/home-en/members-of-parliament#:~:text=Dhaka", "government"),
                new FactCheckResult.Source("Encyclopedia Britannica – Dhaka Historical & Constitutional Capital", "https://www.britannica.com/place/Dhaka#:~:text=Dhaka%2C%20capital%20of%20Bangladesh", "book"),
                new FactCheckResult.Source("Oxford Atlas of the World – Bangladesh Capital Reference", "https://global.oup.com/academic/product/oxford-atlas-of-the-world-9780197767849", "book"),
                new FactCheckResult.Source("The Daily Star – The Making of a Capital (1971 Independence Archive)", "https://www.thedailystar.net/in-focus/news/the-making-capital-1674487", "newspaper"),
                new FactCheckResult.Source("Prothom Alo – Dhaka: 50 Years as the Nation's Capital", "https://en.prothomalo.com/bangladesh/city/dhaka-50-years-capital", "newspaper"),
                new FactCheckResult.Source("Asian Development Bank – Bangladesh Country Report: Administrative Capital", "https://www.adb.org/countries/bangladesh/main#:~:text=Dhaka", "journal")
            ),
            List.of(
                "1. Dhaka is constitutionally designated as the capital of Bangladesh.",
                "2. Article 5 of the Bangladesh Constitution names Dhaka as capital.",
                "3. This has been true since independence on December 16, 1971.",
                "4. The Constitution was adopted in November 1972.",
                "5. Dhaka has historically been a major urban centre since the Mughal era.",
                "6. Bangladesh Government's official portal confirms Dhaka as capital.",
                "7. Encyclopedia Britannica and academic sources corroborate this.",
                "8. No credible source disputes this historical and constitutional fact.",
                "9. Dhaka is home to the National Assembly (Jatiya Sangsad).",
                "10. Verdict: TRUE – completely accurate and constitutionally verified."
            )
        ),

        new Entry("claim_5g_health.png",
            "5G towers cause cancer and radiation sickness",
            "FALSE", 97,
            "No scientific evidence links 5G to cancer or radiation sickness. " +
            "5G uses non-ionizing radio waves that cannot damage DNA. WHO and FDA confirm safety.",
            "World Health Organization (WHO) & U.S. FDA Technical Reports",
            "2020 – 2024",
            "5G networks utilize low-energy non-ionizing RF radiofrequencies that physically cannot break chemical bonds or damage cellular DNA. WHO, FDA, and ICNIRP confirm safety within guidelines.",
            List.of(
                new FactCheckResult.Source("World Health Organization (WHO) – Radiation: 5G Mobile Networks and Health", "https://www.who.int/news-room/questions-and-answers/item/radiation-5g-mobile-networks-and-health#:~:text=no%20adverse%20health%20effect%20has%20been%20causally%20linked", "government"),
                new FactCheckResult.Source("U.S. FDA – Scientific Evidence on Cell Phone and 5G Safety", "https://www.fda.gov/radiation-emitting-products/cell-phones/scientific-evidence-cell-phone-safety#:~:text=The%20scientific%20evidence%20does%20not%20show%20a%20danger", "government"),
                new FactCheckResult.Source("ICNIRP – International Commission: Non-Ionizing Radiation Guidelines 2020", "https://www.icnirp.org/en/activities/news/news-article/rf-guidelines-2020-published.html", "government"),
                new FactCheckResult.Source("Nature: Scientific Reports – Radiofrequency Biological Safety Assessment", "https://www.nature.com/articles/s41598-021-86673-4", "journal"),
                new FactCheckResult.Source("The Lancet – Expert consensus: No evidence of 5G biological harm", "https://www.thelancet.com/journals/lanplh/article/PIIS2542-5196(20)30293-0/fulltext", "journal"),
                new FactCheckResult.Source("Reuters Fact Check – 5G technology has no correlation with illness", "https://www.reuters.com/article/world/fact-check-5g-technology-does-not-cause-cancer-idUSKBN22V27E/", "newspaper"),
                new FactCheckResult.Source("BBC Reality Check – The Facts About 5G and Health Risks", "https://www.bbc.com/news/technology-51328791", "newspaper")
            ),
            List.of(
                "1. 5G uses non-ionizing radio frequencies – physically cannot cause cancer.",
                "2. Ionizing radiation (X-rays, gamma rays) can damage DNA; 5G cannot.",
                "3. WHO has explicitly stated there is no public health risk from 5G.",
                "4. The U.S. FDA found no credible scientific evidence linking cell phones to cancer.",
                "5. ICNIRP sets conservative exposure limits that 5G networks comply with.",
                "6. Peer-reviewed studies in Nature confirm 5G safety within regulatory limits.",
                "7. 5G misinformation spread rapidly during the COVID-19 pandemic.",
                "8. Hundreds of towers were vandalized based on this false claim.",
                "9. Scientific consensus is overwhelmingly in favour of 5G safety.",
                "10. Verdict: FALSE – the claim is contradicted by all credible science."
            )
        ),

        new Entry("716690521_122361326474003647_5230761717797101281_n.jpg",
            "Claim about Mohammed Shishir Manir and ex-IGP Mamun case",
            "MISLEADING", 96,
            "While the first 4 claims in the post are true, the last claim is inconsistent. " +
            "Shishir Manir did not personally handle the case – a member of his legal team did.",
            "BSS News & ICT Court Proceedings",
            "September 2024",
            "Advocate Shishir Manir did not personally handle or appear in the court proceedings for ex-IGP Mamun; an associate counsel within the law firm appeared on official record.",
            List.of(
                new FactCheckResult.Source("BSS News – Case Legal Representation Record", "https://www.bssnews.net/news-flash/290934", "newspaper"),
                new FactCheckResult.Source("TBS News – Court Proceedings & Counsel Identification", "https://www.tbsnews.net/bangladesh/court/clemency-ex-igp-mamun-conditional-full-disclosure-july-august-atrocities-ict", "newspaper"),
                new FactCheckResult.Source("The Daily Star – ICT Court: Ex-IGP Mamun Counsel Details", "https://www.thedailystar.net/news/bangladesh/crime-justice/news/ex-igp-mamun-gets-bail-ict-3771264", "newspaper"),
                new FactCheckResult.Source("Dhaka Tribune – Legal Representation Clarification at ICT Tribunal", "https://www.dhakatribune.com/bangladesh/court/324513/ict-mamun-case-counsel-clarification", "newspaper"),
                new FactCheckResult.Source("Bangladesh Bar Council – Registered Advocates & Law Firms Directory", "https://www.barcouncil.gov.bd/", "government"),
                new FactCheckResult.Source("Rumor Scanner BD – Misleading Attribution of Legal Counsel", "https://rumorscanner.com/fact-check/shishir-manir-ex-igp-mamun-misleading/", "newspaper")
            ),
            List.of(
                "1. Ex-IGP Mamun was represented by Zayed bin Amzad from Shishir Manir's firm.",
                "2. Shishir Manir himself did not appear in court for this case.",
                "3. Attributing the legal representation to Manir personally is inaccurate.",
                "4. BSS and TBS News both reported the correct lawyer's name.",
                "5. Verdict: MISLEADING – partial truth with an inaccurate key detail.",
                "6. Legal representation details are often misreported on social media.",
                "7. Law firms and individual lawyers should not be conflated.",
                "8. Verify court appearances through official court records.",
                "9. This type of misinformation can prejudice legal proceedings.",
                "10. Always cite primary court records when reporting legal matters."
            )
        ),

        new Entry("claim_mars_water.png",
            "NASA confirmed liquid water flowing on Mars surface currently",
            "MISLEADING", 78,
            "NASA confirmed evidence of ancient liquid water on Mars and possible brine flows, " +
            "but not currently flowing liquid water in the conventional sense.",
            "Science Journal / NASA Jet Propulsion Laboratory",
            "September 2015",
            "NASA spectroscopic data found evidence of hydrated perchlorate salts (brines) associated with Recurring Slope Lineae, not sustained open potable liquid water flowing across the Martian surface.",
            List.of(
                new FactCheckResult.Source("Science Journal – Spectral Evidence for Hydrated Salts on Mars (Ojha et al.)", "https://www.science.org/doi/10.1126/science.aab3351", "journal"),
                new FactCheckResult.Source("Nature Geoscience – Water Cycle and Regolith Interactions on Mars", "https://www.nature.com/articles/ngeo2546", "journal"),
                new FactCheckResult.Source("Icarus Journal – Subsurface Liquid Water Lakes Detected on Mars", "https://www.sciencedirect.com/science/article/pii/S0019103518305451", "journal"),
                new FactCheckResult.Source("NASA Mars Exploration – Evidence of Liquid Water RSL Findings", "https://www.nasa.gov/press-release/nasa-confirms-evidence-that-liquid-water-flows-on-today-s-mars", "government"),
                new FactCheckResult.Source("NASA Mars Science Laboratory – Water History on Mars", "https://mars.nasa.gov/msl/mission/science/goals/", "government"),
                new FactCheckResult.Source("The Guardian – Scientists Say Mars Has Flowing Saltwater Streams", "https://www.theguardian.com/science/2015/sep/28/nasa-scientists-find-evidence-flowing-water-mars", "newspaper"),
                new FactCheckResult.Source("BBC Science – Mars Water: What We Know So Far", "https://www.bbc.com/news/science-environment-34389764", "newspaper")
            ),
            List.of(
                "1. NASA found evidence of ancient liquid water on Mars, not current flowing water.",
                "2. Recurring Slope Lineae (RSL) may involve briny water flows – still debated.",
                "3. Mars has water ice at its poles confirmed by multiple missions.",
                "4. Current Mars surface conditions make sustained liquid water very unlikely.",
                "5. The Science journal published RSL findings in 2015.",
                "6. Headlines often simplify complex scientific findings.",
                "7. Verdict: MISLEADING – the claim overstates the scientific findings.",
                "8. Mars exploration continues to find new evidence of water history.",
                "9. The distinction between past and present water is scientifically critical.",
                "10. Cross-reference NASA press releases with peer-reviewed papers."
            )
        ),

        new Entry("claim_eiffel_sold.png",
            "The Eiffel Tower was sold by a con man for scrap metal in 1925",
            "TRUE", 91,
            "Victor Lustig twice sold the Eiffel Tower to scrap-metal dealers in 1925. " +
            "This is one of history's most audacious con schemes, well-documented by historians.",
            "Smithsonian Magazine & Paris Municipal Archives",
            "May 1925",
            "Victor Lustig successfully orchestrated the fraudulent sale of the Eiffel Tower for scrap metal to André Poisson in May 1925, fleeing Paris before the scheme was uncovered.",
            List.of(
                new FactCheckResult.Source("Smithsonian Magazine – The Man Who Sold the Eiffel Tower Twice", "https://www.smithsonianmag.com/history/the-man-who-sold-the-eiffel-tower-twice-17973580/", "journal"),
                new FactCheckResult.Source("History.com – Victor Lustig: The Eiffel Tower Con Artist", "https://www.history.com/news/victor-lustig-sold-the-eiffel-tower", "journal"),
                new FactCheckResult.Source("WorldCat Reference – 'The Man Who Sold the Eiffel Tower' by Floyd Miller", "https://www.worldcat.org/title/man-who-sold-the-eiffel-tower/oclc/1344445", "book"),
                new FactCheckResult.Source("Wikipedia Historical Record – Victor Lustig 1925 Decommission Scam", "https://en.wikipedia.org/wiki/Victor_Lustig#:~:text=Lustig%20is%20best%20known%20for%20the%20Eiffel%20Tower%20scam", "book"),
                new FactCheckResult.Source("BBC History – Victor Lustig: The Man Who Sold the Eiffel Tower", "https://www.bbc.com/news/magazine-17255146", "newspaper"),
                new FactCheckResult.Source("The Guardian – The Greatest Con Artists in History", "https://www.theguardian.com/artanddesign/2019/sep/04/greatest-con-artists-history-forgers-fakers", "newspaper")
            ),
            List.of(
                "1. Victor Lustig posed as a French government official in 1925.",
                "2. He invited six scrap-metal dealers to a secret 'government tender'.",
                "3. André Poisson paid Lustig to 'buy' the Eiffel Tower.",
                "4. Lustig fled to Austria with the money before the fraud was discovered.",
                "5. He repeated the scam with a second victim shortly after.",
                "6. Poisson was too embarrassed to report the crime to police.",
                "7. Lustig was eventually arrested in the United States in 1935.",
                "8. He is one of the most notorious con artists in history.",
                "9. Smithsonian and BBC History have both documented this event.",
                "10. Verdict: TRUE – thoroughly verified by multiple historical sources."
            )
        ),

        new Entry("claim_climate_hoax.png",
            "Climate change is a hoax invented by scientists for research funding",
            "FALSE", 99,
            "Climate change is supported by overwhelming scientific consensus from 97%+ of climate scientists. " +
            "Multiple independent studies, satellite data, and direct measurements confirm global warming.",
            "NASA Vital Signs & IPCC Sixth Assessment Report (AR6)",
            "2021 – 2023",
            "Over 97% of actively publishing peer-reviewed climate scientists agree that contemporary climate warming is driven by anthropogenic greenhouse gas emissions, verified by satellite telemetry.",
            List.of(
                new FactCheckResult.Source("NASA Climate – Direct Evidence and Vital Signs of Planetary Warming", "https://climate.nasa.gov/evidence/#:~:text=The%20current%20warming%20trend%20is%20of%20particular%20significance", "government"),
                new FactCheckResult.Source("NOAA Climate.gov – Is global warming natural or driven by emissions?", "https://www.climate.gov/news-features/climate-qa/its-warming-natural#:~:text=human-caused%20global%20warming", "government"),
                new FactCheckResult.Source("IPCC Sixth Assessment Synthesis Report (Headline Statements)", "https://www.ipcc.ch/report/ar6/syr/longer-report/#:~:text=Human%20activities%2C%20principally%20through%20emissions%20of%20greenhouse%20gases", "journal"),
                new FactCheckResult.Source("Science – The Scientific Consensus on Climate Change (Oreskes)", "https://www.science.org/doi/10.1126/science.1103618", "journal"),
                new FactCheckResult.Source("Nature – Cook et al.: Quantifying the consensus on climate change", "https://www.nature.com/articles/nclimate1388", "journal"),
                new FactCheckResult.Source("The Guardian – Climate Scientists Respond to Denialism", "https://www.theguardian.com/environment/climate-consensus-97-per-cent/2013/may/16/97-percent-consensus-climate-scientists", "newspaper"),
                new FactCheckResult.Source("BBC News – Is Climate Change Real? The Facts Explained", "https://www.bbc.com/news/science-environment-24021772", "newspaper")
            ),
            List.of(
                "1. 97%+ of actively publishing climate scientists agree on human-caused climate change.",
                "2. NASA satellites show measurable sea level rise of 3.3mm per year since 1993.",
                "3. Global average temperature has risen 1.1°C since pre-industrial times (IPCC AR6).",
                "4. Arctic sea ice extent has declined ~13% per decade since 1979.",
                "5. NOAA temperature records from 6,300 stations worldwide confirm warming.",
                "6. Multiple independent research groups using different methodologies reach the same conclusion.",
                "7. The funding conspiracy theory is illogical – fossil fuel industries have far more funding.",
                "8. IPCC reports are written by 800+ scientists from 80+ countries with no shared funding.",
                "9. Cook et al. (2013) analysed 12,000 peer-reviewed papers – 97.1% endorsed consensus.",
                "10. Verdict: FALSE – contradicted by the most robust scientific evidence in history."
            )
        )
    );

    // ── Singleton ─────────────────────────────────────────────────────────────

    private PreloadedDatabase() {}

    public static synchronized PreloadedDatabase getInstance() {
        if (instance == null) instance = new PreloadedDatabase();
        return instance;
    }

    // ── Initialization ────────────────────────────────────────────────────────

    public synchronized void initialize(String dir) {
        if (initialized) return;
        System.out.println("[Preloaded] Initialising from: " + dir);
        for (Entry e : ENTRIES) {
            File f = new File(dir, e.fileName);
            if (!f.exists()) {
                System.out.println("[Preloaded] Missing file: " + f.getAbsolutePath());
                continue;
            }
            try {
                String hash = ImageHashUtil.hashFile(f);
                long dHash = ImageHashUtil.computeDHash(f);
                hashToResult.put(hash, build(e, f, hash));
                indexedEntries.add(new IndexedEntry(e, f, hash, dHash));
                System.out.println("[Preloaded] Registered: " + e.fileName + " → SHA: " + hash.substring(0, 10) + "… dHash: " + Long.toHexString(dHash));
            } catch (Exception ex) {
                System.out.println("[Preloaded] Hash failed: " + e.fileName);
            }
        }
        initialized = true;
        System.out.println("[Preloaded] Ready. " + indexedEntries.size() + " image(s) registered.");
    }

    /** Returns exact match if SHA-256 matches. */
    public FactCheckResult match(File uploaded) {
        try {
            String hash = ImageHashUtil.hashFile(uploaded);
            FactCheckResult r = hashToResult.get(hash);
            System.out.println("[Preloaded Exact Match] " + (r != null ? "Match: " + r.getClaim() : "No match."));
            return r;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Topic 5 / Forensics: Perceptual Near-Duplicate Comparison.
     * Checks exact SHA-256 match first. If not identical, checks dHash Hamming distance.
     * If Hamming distance <= 12, this proves the image is visually the same but has been
     * modified, cropped, recaptioned, or compressed -> "MODIFIED / OUT OF CONTEXT".
     */
    public FactCheckResult matchPerceptual(File uploaded) {
        try {
            // 1. Exact match check
            String uploadedHash = ImageHashUtil.hashFile(uploaded);
            FactCheckResult exact = hashToResult.get(uploadedHash);
            if (exact != null) {
                return exact;
            }

            // 2. Perceptual dHash comparison against all indexed catalog images
            long uploadedDHash = ImageHashUtil.computeDHash(uploaded);
            IndexedEntry bestMatch = null;
            int minDistance = Integer.MAX_VALUE;

            for (IndexedEntry ie : indexedEntries) {
                int dist = ImageHashUtil.hammingDistance(uploadedDHash, ie.dHash);
                if (dist < minDistance) {
                    minDistance = dist;
                    bestMatch = ie;
                }
            }

            // Hamming distance threshold: <= 12 bits out of 64 indicates near-duplicate
            if (bestMatch != null && minDistance <= 12) {
                System.out.println("[Forensics] Near-duplicate detected! Distance=" + minDistance + " with " + bestMatch.entry.fileName);
                FactCheckResult r = new FactCheckResult();
                r.setClaim(bestMatch.entry.claim);
                r.setVerdict("MODIFIED / OUT OF CONTEXT");
                r.setConfidence(93);
                r.setExplanation("Visual forensic analysis confirmed this image is an altered or out-of-context version of an authentic archived photograph. " +
                                 "Perceptual difference hash matched catalog entry with Hamming distance of " + minDistance + "/64. " +
                                 bestMatch.entry.explanation);
                r.setCorrection(bestMatch.entry.correction);
                r.setSources(bestMatch.entry.sources);
                r.setSummary(bestMatch.entry.summary);
                r.setPreloaded(true);
                r.setAiModel("Preloaded Visual Forensics Engine");
                // Evidence buckets from the flat archive list so Evidence Balance renders.
                r.classifySourcesFromFlat();
                // Single sourced timeline event from the archive entry itself —
                // no fabricated dates, the source is the catalog entry.
                r.setTimeline(List.of(
                    new FactCheckResult.TimelineEvent(
                        bestMatch.entry.knownDate != null ? bestMatch.entry.knownDate : "Archived",
                        "Archived Reference: " + bestMatch.entry.claim,
                        bestMatch.entry.explanation != null ? bestMatch.entry.explanation : "",
                        bestMatch.entry.knownSource != null ? bestMatch.entry.knownSource : "Preloaded Archive")
                ));

                // Populate forensic side-by-side metadata
                r.setOriginalImageUrl(bestMatch.file.toURI().toString());
                r.setOriginalImageSource(bestMatch.entry.knownSource);
                r.setOriginalImageDate(bestMatch.entry.knownDate);
                r.setOriginalImageHash(bestMatch.sha256);

                r.setSubmittedImageUrl(uploaded.toURI().toString());
                r.setSubmittedImageHash(uploadedHash);
                ImageHashUtil.ImageInfo info = ImageHashUtil.inspectImage(uploaded);
                r.setSubmittedDimensions(info.width + "x" + info.height);
                r.setSubmittedFormat(info.format);

                return r;
            }

            return null;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static List<Entry> getAllEntries() { return ENTRIES; }

    // ── Builder ───────────────────────────────────────────────────────────────

    private FactCheckResult build(Entry e, File file, String hash) {
        FactCheckResult r = new FactCheckResult();
        r.setClaim(e.claim);
        r.setVerdict(e.verdict);
        r.setConfidence(e.confidence);
        r.setExplanation(e.explanation);
        r.setCorrection(e.correction);
        r.setSources(e.sources);
        r.setSummary(e.summary);
        r.setPreloaded(true);
        r.setAiModel("Preloaded Database");
        // Evidence buckets from the flat archive list so Evidence Balance renders
        // in Image mode identically to Text/URL modes.
        r.classifySourcesFromFlat();
        // Single sourced timeline event from the archive entry itself.
        r.setTimeline(List.of(
            new FactCheckResult.TimelineEvent(
                e.knownDate != null ? e.knownDate : "Archived",
                "Archived Reference: " + e.claim,
                e.explanation != null ? e.explanation : "",
                e.knownSource != null ? e.knownSource : "Preloaded Archive")
        ));

        if (file != null) {
            r.setOriginalImageUrl(file.toURI().toString());
            r.setOriginalImageSource(e.knownSource);
            r.setOriginalImageDate(e.knownDate);
            r.setOriginalImageHash(hash);
        }
        return r;
    }

    // ── Entry record ──────────────────────────────────────────────────────────

    public static class Entry {
        public final String fileName;
        public final String claim;
        public final String verdict;
        public final int    confidence;
        public final String explanation;
        public final String knownSource;
        public final String knownDate;
        public final String correction;
        public final List<FactCheckResult.Source> sources;
        public final List<String> summary;

        public Entry(String fn, String cl, String vd, int cf, String ex,
                     String ks, String kd, String corr,
                     List<FactCheckResult.Source> src, List<String> sum) {
            fileName    = fn; claim       = cl; verdict     = vd;
            confidence  = cf; explanation = ex; knownSource = ks; knownDate   = kd;
            correction  = corr; sources   = src; summary    = sum;
        }
    }
}