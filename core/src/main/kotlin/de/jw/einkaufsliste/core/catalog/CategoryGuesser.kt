package de.jw.einkaufsliste.core.catalog

/** Guesses a category for a typed German item name ("Vollmilch" -> "milch_eier"). */
object CategoryGuesser {
    /**
     * A [CategoryCatalog] slug, or null when nothing fits. The most specific (longest) keyword wins.
     *
     * Keywords match as parts of words, so compounds work ("Vollmilch", "Hackfleisch");
     * keywords of up to three letters ("Ei", "Eis", "Tee") match whole words only. On
     * equal length the keyword further right wins (the head of a German compound).
     * Freezer markers ("TK", "tiefgekühlt", "Pizza", "Eis" as a word) decide the
     * category unless the winning keyword contains the marker itself ("Pizzateig").
     */
    fun guess(name: String): String? = guessWords(words(name))

    /** [guess] for a name already split into [words]. */
    internal fun guessWords(words: List<String>): String? {
        if (words.isEmpty()) return null
        val winner = index.find(words)
        val joined = words.joinToString("")
        val marker = words.firstOrNull { it in FREEZER_WORDS } ?: FREEZER_PARTS.firstOrNull { it in joined }
        if (marker != null && (winner == null || marker !in winner.text)) return FREEZER_SLUG
        return winner?.value
    }

    /** Lowercase words without umlauts, accents, digits, units and punctuation ("1,5 l H-Milch" -> [h, milch]). */
    internal fun words(text: String): List<String> = KeywordIndex.words(text)

    /** All keywords as (normalized text, slug), for tests. */
    internal val entries: List<Pair<String, String>> get() = index.keywords.map { it.text to it.value }

    /** The category slug of freezer items. */
    internal const val FREEZER_SLUG = "tiefkuehl"
    private val FREEZER_WORDS = setOf("tk", "eis")
    private val FREEZER_PARTS = listOf("tiefkuehl", "tiefgekuehlt", "tiefgefroren", "gefroren", "frozen", "pizza")

    /** The keywords behind [guess], valued by category slug; [AssortmentGuesser] reuses them. */
    internal val index: KeywordIndex<String> = KeywordIndex(KeywordIndex.parse(DICTIONARY))
}

/**
 * Keywords per category slug, written as typed (umlauts are normalized). A leading `=`
 * makes a keyword match whole words only ("=kohl" must not match "Holzkohle").
 */
private val DICTIONARY: List<Pair<String, String>> = listOf(
    "obst_gemuese" to """
        obst, apfel, äpfel, banan, birne, orange, mandarine, clementine, klementine, zitron, limette,
        grapefruit, kiwi, mango, ananas, erdbeer, himbeer, heidelbeer, blaubeer, brombeer, johannisbeer,
        stachelbeer, preiselbeer, cranberr, beere, kirsche, pflaume, zwetschge, zwetschke, aprikose, pfirsich,
        nektarine, traube, weintraube, melone, wassermelone, honigmelone, feige, avocado, papaya, maracuja,
        passionsfrucht, physalis, rhabarber, granatapfel, litschi, kaki, kokosnuss, frucht, früchte, obstsalat,
        gemüse, kartoffel, erdäpfel, süßkartoffel, zwiebel, schalotte, knoblauch, karotte, möhre, mohrrübe,
        gurke, salatgurke, tomate, kirschtomate, paprika, salat, kopfsalat, eisbergsalat, feldsalat, rucola,
        rauke, spinat, babyspinat, brokkoli, broccoli, blumenkohl, =kohl, kohlrabi, rotkohl, weißkohl, grünkohl,
        rosenkohl, spitzkohl, wirsing, chinakohl, sauerkraut, zucchini, aubergine, lauch, porree, sellerie,
        fenchel, kürbis, maiskolben, erbsen, zuckerschoten, bohne, grüne bohnen, pilz, champignon, pfifferling,
        radieschen, rettich, meerrettich, rote bete, rote beete, rote rüben, ingwer, petersilie, schnittlauch,
        basilikum, dill, koriander, minze, rosmarin, thymian, salbei, kräuter, frühlingszwiebel, lauchzwiebel,
        artischocke, =okra, pastinake, spargel, chili, suppengrün, kresse, sprossen, salatherz, romana, endivie,
        chicoree, radicchio, mangold, topinambur, steckrübe, schwarzwurzel, fleischtomate
    """,
    "brot_backwaren" to """
        brot, brötchen, semmel, schrippe, =weck, =wecken, =weckle, baguette, toast, croissant, brezel, breze,
        laugen, kuchen, torte, muffin, donut, krapfen, zwieback, ciabatta, bagel, brioche, hefezopf, plunder,
        pumpernickel, wraps, =wrap, =pita, gebäck, backwaren, sandwich, strudel, pfannkuchen, biskuit,
        streuselkuchen, windbeutel, bäckerei, tortilla
    """,
    "milch_eier" to """
        milch, vollmilch, frischmilch, buttermilch, milchprodukte, butter, margarine, käse, gouda, emmentaler,
        edamer, tilsiter, mozzarella, parmesan, feta, camembert, =brie, harzer, hüttenkäse, frischkäse, quark,
        kräuterquark, joghurt, jogurt, yoghurt, skyr, sahne, schlagsahne, schmand, crème fraîche, saure sahne,
        kefir, ayran, eier, =ei, hühnereier, pudding, kondensmilch, kaffeesahne, kaffeemilch, mascarpone, ricotta,
        halloumi, hirtenkäse, schafskäse, ziegenkäse, hafermilch, haferdrink, mandelmilch, sojamilch, reismilch,
        pflanzendrink, schokomilch, kakaomilch, bananenmilch, butterschmalz, kräuterbutter, babybel, philadelphia,
        leerdammer, kerrygold, =rama, lätta, cremefine, fruchtzwerge, pizzakäse, pizzateig, blätterteig,
        flammkuchenteig, hefeteig, mürbeteig, strudelteig, fertigteig
    """,
    "fleisch_wurst" to """
        fleisch, hackfleisch, =hack, gehacktes, hackepeter, mett, zwiebelmett, wurst, würst, aufschnitt, salami,
        pfeffersalami, schinken, speck, bacon, leber, leberkäse, fleischkäse, schnitzel, kotelett, steak,
        braten, gulasch, rind, schwein, kalb, lamm, hähnchen, hühnchen, huhn, hühner, suppenhuhn, =hendl,
        chicken, geflügel, =pute, puten, truthahn, =ente, =enten, entenbrust, entenkeule, gans, gänse, =reh,
        hirsch, kaninchen, frikadelle, boulette, bulette, cevapcici, döner, gyros, kebab, burger, patties,
        roulade, geschnetzeltes, lende, entrecote, rippchen, spareribs, kassler, kasseler, eisbein, haxe,
        frankfurter, wiener, weißwurst, bockwurst, chorizo, mortadella, lyoner, pastrami, roastbeef,
        corned beef, pulled pork, cordon bleu, wurstsalat, metzgerei
    """,
    "fisch" to """
        fisch, lachs, räucherlachs, seelachs, wildlachs, lachsfilet, lachssteak, fischfilet, hering, matjes,
        rollmops, forelle, garnele, shrimps, krabben, scampi, gambas, muscheln, kabeljau, dorsch, makrele,
        sardine, sardellen, thunfisch, zander, tilapia, pangasius, tintenfisch, calamari, surimi, kaviar,
        backfisch, =aal, =aale, hummer, scholle, barsch, karpfen, heilbutt, seehecht, dorade, sushi
    """,
    "tiefkuehl" to """
        tiefkühl, tiefkühlkost, tiefgekühlt, tiefgefroren, gefroren, =tk, pizza, eiswürfel, speiseeis, eiscreme,
        =eis, vanilleeis, schokoeis, schokoladeneis, erdbeereis, himbeereis, zitroneneis, joghurteis, stieleis,
        wassereis, softeis, eis am stiel, pommes, kroketten, fischstäbchen, rahmspinat, schlemmerfilet, nuggets,
        gemüsepfanne, rösti, =magnum, langnese, iglo, frosta, flammkuchen, frühlingsrollen, germknödel,
        kräuterbaguette, pizzabaguette, nudelpfanne, reispfanne
    """,
    "vorrat" to """
        vorräte, nudel, pasta, spaghetti, penne, fusilli, farfalle, tagliatelle, makkaroni, maccaroni, rigatoni,
        spätzle, gnocchi, ravioli, tortellini, lasagne, teigwaren, reis, basmati, risotto, couscous, bulgur,
        quinoa, hirse, linsen, kichererbsen, kidneybohnen, weiße bohnen, baked beans, mehl, zucker, salz,
        pfeffer, =öl, olivenöl, rapsöl, sonnenblumenöl, speiseöl, bratöl, kokosöl, sesamöl, leinöl, pflanzenöl,
        essig, balsamico, senf, ketchup, mayonnaise, =mayo, remoulade, soße, sauce, pesto, brühe, gemüsebrühe,
        hühnerbrühe, rinderbrühe, fleischbrühe, bratensoße, bratensauce, tomatensoße, brühwürfel, bouillon,
        =fond, gewürz, gewürzgurke, essiggurke, oregano, curry, zimt, muskat, kümmel, paprikapulver, edelsüß,
        vanille, backpulver, natron, hefe, honig, marmelade, konfitüre, gelee, nutella, aufstrich, erdnussbutter,
        müsli, cornflakes, haferflocken, hafer, flakes, cerealien, granola, porridge, konserve, tomatenmark,
        passierte tomaten, dosentomaten, gehackte tomaten, pizzatomaten, pizzamehl, pizzagewürz, suppe, eintopf,
        fertiggericht, instant, ramen, paniermehl, semmelbrösel, stärke, gelatine, puddingpulver, backmischung,
        oliven, kapern, nüsse, nuss, haselnuss, walnuss, cashew, pistazie, mandel, rosinen, trockenobst,
        sonnenblumenkerne, kürbiskerne, chiasamen, leinsamen, flohsamen, sesam, sirup, ahornsirup,
        agavendicksaft, currypaste, kokosmilch, kokos, sojasoße, sojasauce, tabasco, sriracha, chutney,
        dressing, apfelmus, mais, knödel, kartoffelknödel, kartoffelpüree, püree, polenta, grieß, kuvertüre,
        streusel, buchweizen, weizenmehl, zitronensaft, hollandaise, mie nudeln, rübenkraut, maggi, knorr, barilla,
        trockenfrüchte, kräutersalz, tomatensuppe, kartoffelsuppe, erbsensuppe, gulaschsuppe, hühnersuppe,
        gemüsesuppe, kürbissuppe, zwiebelsuppe, salsa, guacamole, hummus, =dip, dips, flocken, antipasti,
        sushireis, maultaschen, weinessig, rotweinessig, erdnussöl, würz, nougatcreme, schokocreme,
        amaranth, udon, wasabi, nori, sambal
    """,
    "suesses_snacks" to """
        süß, snack, schokolade, schoko, chocolate, praline, bonbon, bonbons, gummibär, fruchtgummi, weingummi,
        lakritz, kaugummi, keks, butterkeks, plätzchen, cookie, chips, kartoffelchips, paprikachips,
        tortillachips, nachos, flips, erdnussflips, salzstange, salzbrezel, cracker, popcorn, riegel, naschzeug,
        nascherei, lutscher, marshmallow, waffel, spekulatius, lebkuchen, dominosteine, marzipan, nougat,
        studentenfutter, katzenzunge, erdnuss, erdnüsse, knabber, knabbergebäck, brownie, zuckerwatte,
        traubenzucker, toffifee, haribo, milka, toblerone, duplo, hanuta, kitkat, twix, snickers, bounty, oreo,
        pringles, ritter sport, =mars, smarties, maoam, mentos, milchschnitte, kräuterbonbon, pfefferkuchen
    """,
    "getraenke" to """
        getränk, wasser, mineralwasser, sprudel, saft, säfte, schorle, limo, limonade, zitronenlimonade, cola,
        fanta, sprite, pepsi, eistee, icetea, energydrink, energy drink, red bull, smoothie, nektar, orangensaft,
        apfelsaft, o-saft, traubensaft, kirschsaft, multivitaminsaft, ananassaft, maracujasaft, tomatensaft,
        karottensaft, gemüsesaft, grapefruitsaft, johannisbeersaft, fruchtsaft, tonic, ginger ale,
        bitter lemon, spezi, =mate, club mate, malzbier, kokoswasser, capri sonne, bionade, kombucha, ingwershot
    """,
    "alkohol" to """
        alkohol, bier, =beer, pils, =helles, hefeweizen, weizenbier, weißbier, radler, =alster, alsterwasser,
        wein, rotwein, weißwein, glühwein, apfelwein, weinschorle, sekt, prosecco, champagner, crémant,
        schaumwein, vodka, wodka, whisky, whiskey, =rum, =gin, gin tonic, likör, eierlikör, schnaps, obstler,
        =korn, doppelkorn, tequila, aperol, campari, martini, spirituosen, cognac, brandy, weinbrand, cidre,
        cider, =met, amaretto, baileys, jägermeister, ouzo, absinth, limoncello, kirschwasser, grappa, sherry,
        wermut, mezcal, kräuterlikör
    """,
    "kaffee_tee" to """
        kaffee, espresso, cappuccino, latte macchiato, kaffeebohne, filterkaffee, instantkaffee, nescafé,
        =jacobs, tchibo, dallmayr, lavazza, =tee, teebeutel, grüner tee, grüntee, schwarztee, schwarzer tee,
        kräutertee, früchtetee, pfefferminztee, kamillentee, fencheltee, ingwertee, salbeitee, hagebuttentee,
        jasmintee, matetee, rooibos, chai, matcha, earl grey, kakao, trinkschokolade, heiße schokolade,
        kaffeeweißer, meßmer, teekanne, teefilter, senseo
    """,
    "drogerie" to """
        drogerie, körperpflege, kosmetik, zahnpasta, zahncreme, zahnbürste, zahnseide, mundspülung, mundwasser,
        zahn, shampoo, duschgel, duschbad, dusch, seife, =deo, deodorant, deoroller, deospray, antitranspirant,
        creme, handcreme, sonnencreme, sonnenmilch, sonnenschutz, sonnenspray, lotion, bodylotion, rasier,
        aftershave, watte, wattestäbchen, wattepad, abschminktücher, make-up, makeup, schminke, lippenstift,
        mascara, wimperntusche, kajal, eyeliner, lidschatten, puder, nagellack, nagelfeile, nagelschere, parfum,
        parfüm, eau de toilette, haarspray, haargel, haarfarbe, spülung, conditioner, haarkur, haarmaske, =kamm,
        bürste, tampon, =binden, damenbinden, slipeinlagen, kondom, gleitgel, taschentücher, =tempo,
        kosmetiktücher, feuchttücher, hygiene, intimpflege, lippenpflege, lippenbalsam, labello, nivea, peeling,
        gesichtsmaske, mizellen, gesichtswasser, reinigungsmilch, kontaktlinsen, pinzette, haargummi,
        haarspange, badesalz, schaumbad, badezusatz, ohrstöpsel, colgate, elmex, odol, blend-a-med, durex,
        balea, garnier, l'oréal, gillette, wilkinson, schauma, rexona, sebamed, eucerin, taschentuch
    """,
    "haushalt" to """
        haushalt, reinigung, klopapier, toilettenpapier, wc-papier, küchenrolle, küchenpapier, haushaltsrolle,
        zewa, servietten, spülmittel, spülmaschine, geschirrspül, =tabs, klarspüler, regeneriersalz, streusalz,
        waschmittel, waschpulver, weichspüler, fleckentferner, gallseife, reiniger, putzmittel, putz, schwamm,
        lappen, mikrofaser, wischmopp, mopp, besen, kehrblech, handfeger, kehrschaufel, eimer, müllbeutel,
        mülltüte, müllsäcke, abfallbeutel, gelbe säcke, gefrierbeutel, tiefkühlbeutel, frischhaltefolie,
        alufolie, backpapier, butterbrotpapier, brotdose, frischhaltedose, vorratsdose, beutel, kerze, teelicht,
        streichhölzer, feuerzeug, wc-ente, wc-stein, =wc, duftstein, lufterfrischer, raumduft, staubsaugerbeutel,
        gummihandschuh, einweghandschuh, putzhandschuh, wäscheklammer, wäscheleine, wäschenetz, kleiderbügel,
        bügelbrett, spülbürste, klobürste, wc-bürste, spültuch, geschirrtuch, handtuch, tischdecke, strohhalm,
        trinkhalm, pappteller, pappbecher, plastikbecher, einweggeschirr, staubtuch, fensterleder,
        desinfektion, insekt, mücken, motten, fliegenfänger, mausefalle, ameisen, kochtopf, pfanne, geschirr,
        besteck, teller, =tasse, =tassen, schüssel, messer, dosenöffner, schneidebrett, zahnstocher, backform,
        kuchenform, muffinform, nudelholz, küchenschere, pizzaschneider, =sieb, entkalker, somat, =finish,
        calgon, persil, ariel, lenor, sagrotan, domestos, meister proper, =pril, =fairy, vileda, swiffer,
        toppits, febreze, air wick, destilliert, duschvorhang, wasserfilter, schwämme, fliegen, fliegenklatsche,
        scheuermilch, scheuerpulver, rohrfrei, zitronensäure, förmchen, kartusche, möbelpolitur, glas, gläser
    """,
    "baby" to """
        baby, windel, pampers, babynahrung, babybrei, babymilch, anfangsmilch, folgemilch, =pre, pre-nahrung,
        milchpulver, säuglings, kindernahrung, schnuller, babyflasche, fläschchen, babypuder, wundschutzcreme,
        hipp, alete, milupa, aptamil, bebivita, quetschie, quetschbeutel, beißring, lätzchen, strampler,
        kinderwagen, babyphone, penaten, bübchen
    """,
    "tier" to """
        tierbedarf, tiernahrung, tierfutter, futter, katzenfutter, hundefutter, nassfutter, trockenfutter,
        katzenstreu, einstreu, klumpstreu, kleintierstreu, leckerli, kauknochen, katze, hunde, =hund,
        hundekeks, hundeleine, halsband, kratzbaum, vogelfutter, fischfutter, =heu, nagerfutter,
        kaninchenfutter, meerschwein, hamster, wellensittich, aquarium, katzenklo, whiskas, pedigree, frolic,
        sheba, chappi, dreamies, =felix, kotbeutel, meisenknödel, vogelsand, zoohandlung, hundesnack, katzensnack,
        katzenmilch, hühnerfutter
    """,
    "medikamente" to """
        apotheke, gesundheit, ibuprofen, paracetamol, aspirin, tabletten, schmerz, schmerzmittel, husten,
        hustenbonbon, halstabletten, nasenspray, nasentropfen, nasenspülung, augentropfen, ohrentropfen,
        tropfen, salbe, pflaster, verband, mullbinde, fieberthermometer, thermometer, medikament, medizin,
        arznei, =rezept, vitamin, magnesium, =zink, calcium, kalzium, omega, fischöl, wunddesinfektion,
        antibiotikum, schwangerschaftstest, =ffp, mundschutz, voltaren, dolormin, thomapyrin, grippostad,
        baldrian, johanniskraut, globuli, erkältung, grippe, allergie, antihistamin, loratadin, cetirizin,
        diclofenac, hustenlöser, =pille, insulin, blutdruck, teststreifen, schnelltest, selbsttest, coronatest,
        kompressen, bepanthen, hansaplast, sinupret, iberogast, loperamid, imodium, durchfall, sodbrennen,
        rennie, fenistil, zeckenzange
    """,
    "baumarkt" to """
        baumarkt, schraube, dübel, =nagel, nägel, hammer, bohrer, bohrmaschine, akkuschrauber, säge, zange,
        werkzeug, farbe, wandfarbe, =lack, klarlack, holzlack, lackspray, sprühlack, pinsel, farbrolle,
        malerkrepp, malerband, abdeckfolie, malervlies, silikon, acryl, spachtel, gips, zement, mörtel, fliesen,
        tapete, kleister, dichtung, kabelbinder, kabeltrommel, schleifpapier, schmirgelpapier, holz, bretter,
        draht, vorhängeschloss, wasserwaage, zollstock, maßband, cuttermesser, teppichmesser, rostschutz,
        rostentferner, wasserhahn, duschkopf, rauchmelder, =leiter, sekundenkleber, montagekleber, =leim,
        gewebeband, isolierband, inbus, imbus, unterlegscheibe, scharnier, bauschaum, montageschaum, =kitt,
        fugenmasse, grundierung, lasur, terpentin, verdünnung, pflasterstein
    """,
    "garten" to """
        garten, =erde, blumenerde, pflanzerde, pflanzenerde, gartenerde, anzuchterde, kakteenerde, rasenerde,
        dünger, samen, saatgut, pflanze, blume, blumentopf, blumenzwiebel, rasen, gartenschlauch, schlauch,
        gießkanne, gartenschere, rosenschere, harke, =rechen, spaten, schaufel, rindenmulch, mulch, kompost,
        grill, grillkohle, holzkohle, grillanzünder, anzünder, unkraut, schneckenkorn, vogelhaus, pflanzkübel,
        orchidee, rosen, tulpen, geranien, kaktus, sukkulente, insektenhotel, gartenhandschuh
    """,
    "elektronik" to """
        elektronik, elektro, ladekabel, kabel, =usb, hdmi, ladegerät, netzteil, powerbank, kopfhörer, =handy,
        handyhülle, smartphone, displayschutz, panzerglas, adapter, speicherkarte, sd-karte, usb-stick, =maus,
        computermaus, tastatur, drucker, druckerpatrone, =toner, fernseher, =tv, fernbedienung, lautsprecher,
        bluetooth, router, =tablet, laptop, computer, monitor, festplatte, wasserkocher, toaster, föhn,
        haartrockner, glätteisen, bügeleisen, waffeleisen, sandwichmaker, staubsauger, mikrowelle, =mixer,
        kaffeemaschine, kaffeemühle, steckdose, verlängerungskabel, mehrfachstecker, smartwatch, kamera,
        headset, airpods, konsole, playstation, controller, simkarte, sim-karte, prepaid, lightning, ventilator,
        kühlschrank, gefrierschrank, gefriertruhe, tiefkühltruhe, milchaufschäumer, eierkocher,
        brotbackautomat
    """,
    "batterien_lampen" to """
        batterie, akku, knopfzelle, =aa, =aaa, mignon, glühbirne, led-birne, leuchtmittel, =led, lampe,
        leuchte, halogen, leuchtstoffröhre, glühlampe, lichterkette, varta, duracell, energizer, osram, v-block,
        blockbatterie
    """,
    "schreibwaren" to """
        schreibwaren, kugelschreiber, =kuli, =kulis, stift, bleistift, buntstift, filzstift, füller, fineliner,
        tinte, radiergummi, radierer, anspitzer, spitzer, lineal, geodreieck, zirkel, schere, papier,
        druckerpapier, kopierpapier, block, heft, notizbuch, kalender, ordner, mappe, briefumschlag,
        umschlag, umschläge, briefmarke, haftnotizen, post-it, klebezettel, zettel, textmarker, marker, tesa,
        klebeband, kleber, klebstoff, =uhu, pritt, edding, stabilo, pelikan, geschenkpapier, geschenkband,
        karte, buch, bücher, zeitung, zeitschrift, magazin, comic, wasserfarbe, tuschkasten, knete, bastel,
        etiketten, tacker, heftklammer, büroklammer, locher, korrekturroller, tipp-ex, kreide, kuvert,
        versandtasche, reiseführer
    """,
    "kleidung" to """
        kleidung, socke, strumpf, strümpfe, unterhose, unterwäsche, boxershorts, =bh, =slip, =slips, shirt, hemd,
        bluse, hose, jeans, =rock, kleid, pullover, pulli, hoodie, jacke, mantel, =schal, mütze, handschuh,
        schuh, sneaker, stiefel, sandalen, latschen, flipflops, badeanzug, bikini, schlafanzug, pyjama,
        gürtel, krawatte, leggings, shorts, schnürsenkel, einlegesohle, =weste, anzug
    """,
    "sonstiges" to """
        sonstiges, zigarette, tabak, geschenk, gutschein, spielzeug, luftballon, =deko, dekoration,
        scheibenwischwasser
    """,
)
