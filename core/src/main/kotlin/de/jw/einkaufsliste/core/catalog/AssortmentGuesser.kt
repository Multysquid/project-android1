package de.jw.einkaufsliste.core.catalog

/** Guesses what kind of product an item is ("Batterien" -> [Assortment.ELEKTRO_KLEINTEILE]). */
object AssortmentGuesser {
    /**
     * Assortments for the item [name], or for its Unterliste [categorySlug] when the name
     * gives nothing; empty when neither helps (the item is then looked for in broad stores).
     *
     * Freezer markers ("TK", "tiefgekühlt", see [CategoryGuesser.guess]) mean [Assortment.TIEFKUEHL].
     * Otherwise the longest keyword decides, taken from a dictionary finer than the Unterlisten
     * (mostly non-food) and from [CategoryGuesser]'s, whose categories count as [forCategory];
     * on the same keyword the finer dictionary wins. In "Batterien für die Fernbedienung" the
     * part before "für", "zum", "zur" or "gegen" is the product and is tried first.
     */
    fun guess(name: String, categorySlug: String?): Set<Assortment> {
        val head = name.split(PURPOSE, limit = 2).first()
        if (head.length < name.length) guessName(head)?.let { return it }
        return guessName(name) ?: forCategory(categorySlug)
    }

    private fun guessName(name: String): Set<Assortment>? {
        val words = KeywordIndex.words(name)
        if (words.isEmpty()) return null
        if (CategoryGuesser.guessWords(words) == CategoryGuesser.FREEZER_SLUG) return FREEZER
        return index.find(words)?.value
    }

    /** Assortments of a default Unterliste ([CategoryCatalog] slug); empty for "Sonstiges" and custom ones. */
    fun forCategory(slug: String?): Set<Assortment> = slug?.let { byCategory[it] }.orEmpty()

    /** The finer dictionary's keywords, for tests. */
    internal val fineKeywords: List<KeywordIndex.Keyword<Set<Assortment>>> by lazy {
        KeywordIndex.parse(DICTIONARY.map { (assortment, list) -> setOf(assortment) to list } + SHARED_DICTIONARY)
    }

    /** All keywords [guess] uses as (normalized text, assortments), the finer ones first, for tests. */
    internal val entries: List<Pair<String, Set<Assortment>>> get() = index.keywords.map { it.text to it.value }

    private val FREEZER = setOf(Assortment.TIEFKUEHL)
    private val PURPOSE = Regex("\\s+(?:für|fuer|zum|zur|gegen)\\s+", RegexOption.IGNORE_CASE)

    private val byCategory: Map<String, Set<Assortment>> = buildMap {
        for (food in FOOD) put(food.slug, setOf(food))
        put("drogerie", setOf(Assortment.KOERPERPFLEGE))
        put("haushalt", setOf(Assortment.HAUSHALT_REINIGUNG))
        put("baby", setOf(Assortment.BABY))
        put("tier", setOf(Assortment.TIER))
        put("medikamente", setOf(Assortment.MEDIKAMENTE))
        put("baumarkt", setOf(Assortment.WERKZEUG))
        put("garten", setOf(Assortment.GARTEN))
        put("elektronik", setOf(Assortment.ELEKTRONIK))
        put("batterien_lampen", setOf(Assortment.ELEKTRO_KLEINTEILE))
        put("schreibwaren", setOf(Assortment.SCHREIBWAREN))
        put("kleidung", setOf(Assortment.KLEIDUNG))
    }

    private val index: KeywordIndex<Set<Assortment>> by lazy {
        val fineTexts = fineKeywords.mapTo(HashSet()) { it.text }
        val coarse = CategoryGuesser.index.keywords.mapNotNull { keyword ->
            val assortments = forCategory(keyword.value)
            if (assortments.isEmpty() || keyword.text in fineTexts) null
            else KeywordIndex.Keyword(keyword.text, assortments, keyword.wholeWord)
        }
        KeywordIndex(fineKeywords + coarse)
    }
}

/** Food assortments; each has the slug of the Unterliste with the same food. */
private val FOOD = listOf(
    Assortment.OBST_GEMUESE,
    Assortment.BROT_BACKWAREN,
    Assortment.MILCH_EIER,
    Assortment.FLEISCH_WURST,
    Assortment.FISCH,
    Assortment.TIEFKUEHL,
    Assortment.VORRAT,
    Assortment.SUESSES_SNACKS,
    Assortment.GETRAENKE,
    Assortment.ALKOHOL,
    Assortment.KAFFEE_TEE,
)

/**
 * Keywords per assortment in [CategoryGuesser]'s format (a leading `=` matches whole words
 * only), finer than its categories and mostly for what is not food. Its keywords whose
 * category stands for the wrong assortment are listed again here, and so are longer ones
 * that contain a keyword split off from their category ("Handschuh" contains "Schuh").
 */
private val DICTIONARY: List<Pair<Assortment, String>> = listOf(
    Assortment.OBST_GEMUESE to """
        rotkraut, blaukraut
    """,
    Assortment.MILCH_EIER to """
        topfen, spiegelei, crème double
    """,
    Assortment.VORRAT to """
        lebensmittelfarbe, eierfarbe, schokotropfen, glasur, marinade, reispapier, esspapier, oblaten
    """,
    Assortment.SUESSES_SNACKS to """
        adventskalender
    """,
    Assortment.ALKOHOL to """
        feuerzangenbowle, =bowle, punsch
    """,
    Assortment.KOERPERPFLEGE to """
        abschminktücher, kosmetiktücher, deostift, haaröl, haarwasser, haarwachs, schaumfestiger, haarklammer,
        haarreif, haarband, tönung, =kämme, läusekamm, badekugel, körperöl, massageöl, sonnenöl, after sun, bartöl,
        rasierwasser, rasierpinsel, rasiermesser, menstruationstasse, inkontinenz, nagelknipser, hornhaut,
        fußpflege, vaseline, gebissreiniger, prothesenreiniger, dove, syoss, isana, =axe
    """,
    Assortment.KOSMETIK to """
        kosmetik, make-up, makeup-pinsel, schminke, schmink, lippenstift, lipgloss, lippenkonturenstift, mascara,
        wimperntusche, wimpern, kajal, kajalstift, eyeliner, lidschatten, augenbrauen, puder, puderpinsel, =rouge,
        foundation, concealer, abdeckstift, bronzer, nagellack, kunstnägel, gelnägel, parfum, parfüm,
        eau de toilette, duftwasser, maybelline, catrice
    """,
    Assortment.GESUNDHEIT to """
        gesundheit, pflaster, hansaplast, verband, mullbinde, kompressen, bandage, vitamin, magnesium, =zink,
        vitamintablette, vitaminctablette, vitamindtablette, magnesiumtablette, zinktablette, calcium, kalzium,
        calciumtablette, eisentablette, omega, fischöl, folsäure, nahrungsergänzung, baldrian, melatonin, kondom,
        gleitgel, durex, schwangerschaftstest, ovulationstest, fieberthermometer, thermometer, blutdruckmessgerät,
        händedesinfektion, wunddesinfektion, wundspray, kontaktlinsen, lesebrille, brillenputztücher, ohrstöpsel,
        ohropax, wärmflasche, kühlpad, coolpack, kirschkernkissen, =ffp, mundschutz, schnelltest, selbsttest,
        coronatest, zeckenzange, mückenschutz, mückenspray, autan, nasenspülung
    """,
    Assortment.MEDIKAMENTE to """
        =tabletten, =tablette, lutschtablette, schlaftablette, kohletablette, magentablette, =ibu, =ass, novalgin,
        novaminsulfon, medinait, =wick, cortison, kortison, antibiotika, penicillin, homöopathie, fiebersaft,
        halsspray, zäpfchen, octenisept, betaisodona, heuschnupfen, verstopfung, abführmittel, blutzucker,
        tantum verde, dobendan, neo-angin, mucosolvan, prospan, gelomyrtol, olynth, otriven, nasic, aciclovir
    """,
    Assortment.HAUSHALT_REINIGUNG to """
        geschirrspül, spültabs, hygienespüler, waschmaschinenreiniger, flächendesinfektion, bügelwasser,
        textilfarbe, stofffarbe, papierhandtuch, papierhandtücher, pergamentpapier, tüte, stabfeuerzeug, abzieher,
        haushaltshandschuh, staubwedel, flecken, möbelpolitur, cillit, =ajax, glorix
    """,
    Assortment.HAUSHALTSWAREN to """
        haushaltswaren, kochtopf, kochtöpfe, =topf, =töpfe, topfset, topfdeckel, deckel, pfanne, =wok, geschirr,
        teller, =tasse, =tassen, kaffeetasse, teetasse, =becher, kaffeebecher, thermobecher, eierbecher, messbecher,
        glas, gläser, besteck, messer, gabel, kuchengabel, löffel, schüssel, =sieb, nudelsieb, teesieb,
        schneidebrett, dosenöffner, flaschenöffner, korkenzieher, =reibe, käsereibe, gemüsereibe, reibeisen,
        sparschäler, kartoffelschäler, knoblauchpresse, zitronenpresse, salatschleuder, schneebesen, =kelle,
        suppenkelle, schöpfkelle, thermoskanne, =kanne, kaffeekanne, brotdose, brotbox, lunchbox, frischhaltedose,
        vorratsdose, butterdose, tupper, brotkasten, backform, kuchenform, muffinform, springform, auflaufform,
        backblech, ausstecher, förmchen, nudelholz, spritzbeutel, backpinsel, küchenwaage, küchenschere,
        pizzaschneider, salzstreuer, pfeffermühle, gewürzmühle, kühltasche, kühlakku, trinkflasche, korb, körbe,
        papierkorb, wäscheständer, bügelbrett, kleiderbügel, mülleimer, abfalleimer, treteimer, abtropfgestell,
        =tablett, =tabletts, serviertablett, seifenspender, flaschenbürste, einkaufstasche, spieß
    """,
    Assortment.DEKO_WOHNEN to """
        deko, tischdeko, duftkerze, stumpenkerze, kerzenhalter, kerzenständer, windlicht, bilderrahmen, fotorahmen,
        =rahmen, =vase, =vasen, blumenvase, kunstblume, kunstpflanze, spiegel, wanduhr, =poster, lampenschirm,
        stehlampe, tischlampe, schreibtischlampe, nachttischlampe, deckenlampe, deckenleuchte, hängelampe,
        wandlampe, wandleuchte, christbaumkugel, weihnachtskugel, weihnachtsschmuck, christbaumschmuck, adventskranz
    """,
    Assortment.HEIMTEXTIL to """
        heimtextilien, bettwäsche, bettbezug, laken, kissenbezug, kopfkissen, decke, tischdecke, tischläufer,
        tischset, handtuch, handtücher, duschtuch, badetuch, badetücher, waschlappen, badematte, badvorleger,
        duschvorleger, duschvorhang, vorhang, vorhänge, gardine, geschirrtuch, geschirrtücher, topflappen,
        ofenhandschuh, teppich, fußmatte
    """,
    Assortment.MOEBEL to """
        möbel, stuhl, stühle, hocker, tisch, regal, schrank, kommode, sofa, couch, sessel, =bett, =betten,
        bettgestell, boxspringbett, hochbett, kinderbett, lattenrost, matratze, garderobe, vitrine, sideboard
    """,
    Assortment.WERKZEUG to """
        panzertape, duct tape, wd-40, kriechöl, schmieröl, sandpapier, farbroller, teppichmesser, klebepistole,
        arbeitshandschuh, schutzbrille, gehörschutz, lötkolben, lötzinn, multimeter, winkelschleifer,
        schleifmaschine, werkbank, beton, rigips, laminat, teppichboden, fußleiste, sockelleiste, siphon, klobrille,
        toilettensitz, wc-sitz, spülkasten, türklinke, briefkasten, hausnummer, =muttern, haken, =bits, =leitern,
        trittleiter, stehleiter, bewegungsmelder, stahlwolle, gewindestange, schloss, vorhängeschloss,
        duschschlauch, =seil, drahtbürste, eisenwaren
    """,
    Assortment.GARTEN to """
        blumenerde, blumentopf, blumentöpfe, pflanztopf, pflanztöpfe, blumenzwiebel, blumenkasten, tulpenzwiebel,
        steckzwiebel, tomatensamen, kräutersamen, gemüsesamen, rosenschere, laubrechen, heckenschere, astschere,
        brikett, grillzange, grillbesteck, grillbürste, gasflasche, propan, blaukorn, laubsack, laubsäcke,
        laubbesen, schubkarre, torf, sonnenschirm, gartenmöbel, liegestuhl, weihnachtsbaum, tannenbaum, christbaum,
        mähroboter, hochbeet, gewächshaus, buchsbaum, =kohle, lampenöl
    """,
    Assortment.ELEKTRO_KLEINTEILE to """
        verlängerungskabel, steckdose, mehrfachstecker, =stecker, reiseadapter, zeitschaltuhr, kabeltrommel,
        nachtlicht, lichtschalter, cr2032
    """,
    Assortment.ELEKTRONIK to """
        ohrhörer, micro-sd, mauspad, notebook, handyakku, handyhalterung, bildschirm, webcam, mikrofon, beamer,
        scanner, radio, wecker, nintendo, xbox, videospiel, =cd, =dvd, blu-ray, guthabenkarte
    """,
    Assortment.HAUSHALTSGERAETE to """
        haushaltsgeräte, wasserkocher, toaster, staubsauger, akkusauger, saugroboter, föhn, fön, haartrockner,
        glätteisen, lockenstab, =mixer, stabmixer, pürierstab, handmixer, standmixer, küchenmaschine,
        kaffeemaschine, vollautomat, espressomaschine, kapselmaschine, kaffeemühle, bügeleisen, waffeleisen,
        sandwichmaker, mikrowelle, kühlschrank, gefrierschrank, gefriertruhe, tiefkühltruhe, waschmaschine,
        wäschetrockner, =trockner, ventilator, heizlüfter, luftreiniger, luftbefeuchter, luftentfeuchter,
        eierkocher, milchaufschäumer, eismaschine, brotbackautomat, fritteuse, airfryer, reiskocher, entsafter,
        saftpresse, dampfreiniger, nähmaschine, heizdecke, personenwaage, körperwaage, bartschneider, haarschneider,
        elektrorasierer
    """,
    Assortment.SCHREIBWAREN to """
        notizbuch, schreiber, karteikarte, klarsichthülle, prospekthülle, laminierfolie, taschenrechner, mäppchen,
        schulranzen, schulrucksack, malkasten, malbuch, malpinsel, acrylfarbe, stempel, fotoalbum, paketband,
        packband, reißzwecken, nadel, nähgarn, =garn, =wolle
    """,
    Assortment.BUECHER to """
        buch, bücher, =roman, =romane, =krimi, =krimis, reiseführer, landkarte, stadtplan, =atlas
    """,
    Assortment.ZEITSCHRIFTEN_TABAK to """
        zeitung, zeitschrift, illustrierte, magazin, rätsel, sudoku, zigarette, zigarillo, zigarre, tabak,
        =blättchen, stopfhülsen, feuerzeug, =lotto, lottoschein, rubbellos, eurojackpot
    """,
    Assortment.KLEIDUNG to """
        handschuh, =tasche, =taschen, handtasche, umhängetasche, schirm, sonnenbrille, =hut, =hüte, =cap, =kappe,
        portemonnaie, geldbörse, geldbeutel, schmuck, ohrringe, armbanduhr
    """,
    Assortment.SCHUHE to """
        schuh, sneaker, stiefel, sandalen, latschen, flipflops, schnürsenkel, einlegesohle, schuhlöffel,
        schuhbürste, pantoffeln, crocs, birkenstock, ballerina, =pumps, slipper, =clogs, =boots
    """,
    Assortment.SPIELZEUG to """
        spielzeug, spiel, spielkarten, puzzle, lego, playmobil, puppe, kuscheltier, stofftier, plüschtier, teddy,
        seifenblasen, wasserpistole, murmeln, kreisel, flummi, =drachen, bobbycar, ferngesteuert, matchbox, barbie,
        bauklötze, tretroller
    """,
    Assortment.SPORT to """
        =sport, sporttasche, sportbeutel, turnbeutel, yogamatte, hantel, fitnessband, springseil, gymnastikball,
        fußball, fußbälle, tennisball, tennisbälle, schläger, volleyball, basketball, handball, federball,
        badminton, tischtennis, schwimmbrille, taucherbrille, skibrille, badekappe, schnorchel, schwimmflügel,
        luftmatratze, isomatte, =zelt, =zelte, schlafsack, rucksack, wanderstöcke, gaskartusche, camping, =angel,
        angelrute, angelschnur, skateboard, inliner, =helm, skihelm, schienbeinschoner, =ski, snowboard, schlitten,
        frisbee, =boule
    """,
    Assortment.FAHRRAD to """
        fahrrad, fahrradschlauch, fahrradschloss, =rad, =räder, =bike, e-bike, mountainbike, flickzeug, =flicken,
        rücklicht, frontlicht, vorderlicht, =sattel, satteltasche, luftpumpe, standpumpe, kettenöl, kettenspray,
        =ventil, =ventile, speichen, klingel, gepäckträger, schutzblech, laufrad
    """,
    Assortment.AUTO to """
        =auto, autozubehör, autowäsche, autopflege, autoshampoo, autopolitur, autowachs, autoschwamm, autobatterie,
        autoreifen, autoduft, =reifen, winterreifen, sommerreifen, scheibenwischwasser, wischwasser,
        scheibenreiniger, felgenreiniger, scheibenwischer, wischerblätter, enteiser, eiskratzer, frostschutz,
        kühlwasser, kühlerwasser, batteriewasser, motoröl, getriebeöl, adblue, =benzin, =diesel, kanister,
        starthilfekabel, abschleppseil, warnweste, warndreieck, cockpitspray, duftbaum, vignette, parkscheibe
    """,
    Assortment.PARTY_GESCHENK to """
        party, geschenk, geschenkpapier, geschenkband, präsentkorb, gutschein, karte, ballon, konfetti, girlande,
        luftschlange, wunderkerze, geburtstagskerze, kuchenkerze, tortenkerze, zahlenkerze, kostüm, verkleidung,
        feuerwerk, böller, =rakete, =raketen, silvester, mitbringsel
    """,
    Assortment.BLUMEN to """
        blume, strauß, rosen, tulpen, lilien, gerbera, narzissen, osterglocken, gesteck
    """,
    Assortment.BABY to """
        babyshampoo, stilleinlagen, milchpumpe, =sauger, nasensauger, wickel, buggy, tragetuch, kindersitz,
        hochstuhl, rassel, spucktuch, kindermilch, gläschen
    """,
    Assortment.TIER to """
        hundespielzeug, katzenspielzeug, hundeshampoo, wurmkur, wurmtablette, flohmittel, =napf, fressnapf,
        terrarium, käfig
    """,
)

/** Keywords for what really is sold as two assortments. */
private val SHARED_DICTIONARY: List<Pair<Set<Assortment>, String>> = listOf(
    setOf(Assortment.HAUSHALT_REINIGUNG, Assortment.DEKO_WOHNEN) to "kerze, teelicht, grablicht",
    setOf(Assortment.HAUSHALT_REINIGUNG, Assortment.PARTY_GESCHENK) to
        "servietten, pappteller, pappbecher, plastikbecher, einweggeschirr, strohhalm, trinkhalm",
    setOf(Assortment.HAUSHALT_REINIGUNG, Assortment.HAUSHALTSGERAETE) to "staubsaugerbeutel",
    setOf(Assortment.HAUSHALT_REINIGUNG, Assortment.GESUNDHEIT) to "desinfektion",
    setOf(Assortment.HAUSHALT_REINIGUNG, Assortment.SCHUHE) to "schuhcreme",
    setOf(Assortment.HAUSHALT_REINIGUNG, Assortment.WERKZEUG) to "spiritus",
    setOf(Assortment.KOERPERPFLEGE, Assortment.BABY) to "feuchttücher",
    setOf(Assortment.SUESSES_SNACKS, Assortment.GESUNDHEIT) to "hustenbonbon, ricola, em-eukal",
    setOf(Assortment.KAFFEE_TEE, Assortment.MEDIKAMENTE) to "hustentee, erkältungstee",
    setOf(Assortment.SCHREIBWAREN, Assortment.WERKZEUG) to "kleber, klebstoff, klebeband",
    setOf(Assortment.SCHREIBWAREN, Assortment.ELEKTRONIK) to "tintenpatrone",
    setOf(Assortment.SCHREIBWAREN, Assortment.SPIELZEUG) to "knete",
    setOf(Assortment.BUECHER, Assortment.ZEITSCHRIFTEN_TABAK) to "comic",
    setOf(Assortment.DEKO_WOHNEN, Assortment.HEIMTEXTIL) to "kissen",
    setOf(Assortment.SPORT, Assortment.SPIELZEUG) to "=ball, =bälle, wasserball",
    setOf(Assortment.AUTO, Assortment.GESUNDHEIT) to "verbandskasten",
)
