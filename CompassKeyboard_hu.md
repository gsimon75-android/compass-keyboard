# Bevezetés #
A CompassKeyboard legfőbb célja, hogy lehetővé tegye tetszőleges karakter (beleértve a nemzeti karaktereket és a programozási nyelvek szimbólumait is) bevitelét egyazon kiosztás használatával.

Az alapértelmezett kiosztások támogatják a latin, cirill illetve a görög ábécére épülő nyelvek karaktereit, de van lehetőség a felhasználó által definiált kiosztások használatára is.


# Használat #
Mivel a virtuális billentyűzeteket nem a hagyományos gépírással használjuk, mind a szokásos 'qwerty', mind a dvorak elrendezés elvesztené az előnyeit, így itt a gombok funkcionális csoportokba vannak rendezve, a betűk abc-sorrendben, az ékezetes karakterek pedig az ékezet nélküli alap-karakterük módosulataiként érhetőek el.

Mindezen karakterek támogatásához rengeteg gombra lenne szükség, többre, mint amennyi egy virtuális billentyűzeten elfér, így a billentyűk használatát ki kellett bővíteni: a hagyományos megérintéssel való aktiválásukon túl lehetőség van az iránytű nyolc alap-irányába történő 'elhúzásukra' is, ekkor a billentyű által előállított karakter ezen elhúzás irányától fog függeni. Ezeknek a húzó mozdulatoknak nem kell hosszúnak lenniük, a lényeg annyi, hogy a billentyűn kezdődjenek és valamerre azon kívül érjenek véget.

Ezen módon kilencszer annyi karaktert tudunk bevinni, mint a hagyományos, csak érintést vizsgáló billentyűzettel, bár kétségtelenül némi időbe telik, amíg rááll az ember keze (az abc szerinti elrendezésnek köszönhetően a rátanulás elég gyorsan megtörténik).

![http://compass-keyboard.googlecode.com/files/usage.png](http://compass-keyboard.googlecode.com/files/usage.png)


# Alapértelmezett kiosztások #

Jelenleg a CompassKeyboard három alapértelmezett kiosztást támogat, egyet a latin-alapú ábécékhez, egyet a cirill-alapúakhoz, egyet pedig a göröghöz.

A kiosztások (beépítettek és egyediek egyaránt) között a billentyűzet elhagyása nélkül is válthatunk, ehhez csak egy nagy átlós vonalat kell húzni a billentyűzet bal-felső sarkából a jobb-alsóba ('Spec' módosító, lásd lentebb), majd a bal-felső gombon megjelenő L0..L6 közül kiválaszthatjuk a kívánt kiosztást.


## Beállítások ##
A beállítások között megtekinthetjük az aktuálisan elérhető kiosztásokat. Ezek közül az első három a beépített kiosztások, ezek nem változtathatóak meg, viszont szabadon adhatunk a listához továbbiakat XML file-okból az utolsó menüpont használatával. Ezen egyedileg hozzáadott kiosztások azonnal megjelennek a listában, feltéve, hogy szintaktikailag helyesek voltak.
Az egyedileg hozzáadott kiosztásokat el is távolíthatjuk a listáról, csak meg kell érintenünk őket, majd a döntésünket jóváhagyni az ezután megjelenő ablakban (fontos tudni, hogy ez csak a listáról távolítja el a kiosztásokat, az őket tartalmazó XML file-ok változatlanul maradnak).
![http://compass-keyboard.googlecode.com/files/layout_settings_v1.1.png](http://compass-keyboard.googlecode.com/files/layout_settings_v1.1.png)


## Latin ##
![http://compass-keyboard.googlecode.com/files/latin.png](http://compass-keyboard.googlecode.com/files/latin.png)

## Cirill ##
![http://compass-keyboard.googlecode.com/files/cyrillic.png](http://compass-keyboard.googlecode.com/files/cyrillic.png)

## Görög ##
![http://compass-keyboard.googlecode.com/files/greek.png](http://compass-keyboard.googlecode.com/files/greek.png)


# Módosítók #
Különösen a latin ábécék esetén, az alap karaktereken felül számos ékezetes betű is használatos: jobbra- ill. balra dőlő ékezet, kettős ékezet, kalap, kettőspont, ékjel, felülvonás, pont, karika vagy hullám -hogy csak a leggyakoribbakat említsük-, de némely nyelvek áthúzott betűket is használnak. A CompassKeyboard ezeket is támogatja, még mindig ugyanazon egyetlen elrendezésben. Mivel ennyi karakter már végképp túl sok billentyűt igényelne, még a 9-irányúakból is, egy újabb dolgot kellett bevezetni: a módosítókat.

Ha húzunk egy nagy átlós vonalat a billentyűzet valamely sarkából az átellenesbe, egy nagy vízszinteset az egyik oldal közepétől a szemköztiig, vagy függőlegeset középen fentről lefelé vagy vissza, akkor  egy 'ékezetes módot' választunk ki, hasonlóképpen egy hagyományos billentyűzet váltóbillentyűjének a megnyomásához. Ezen ékezetes módokban a billentyűk képe és viselkedése megváltozik, az alap betűiknek az adott ékezetes megfelelőit mutatják és teszik elérhetővé. Ezen ékezetes mód hatása a következő bevitt betűig tart, de ha szeretnénk bevitel nélkül visszalépni a sima módba, csak érintsünk meg vagy húzzunk el egy gombot egy olyan irányba, amihez nincsen ékezetes karakter rendelve.

Ha nagybetűs ékezetes betűket szeretnénk, akkor a 'shift' megérintésével  váltsunk előbb nagybetűkre, majd a fenti módon válasszuk ki a kívánt ékezetes módot.

## Vízszintes ##
![http://compass-keyboard.googlecode.com/files/global_swipe_horizontal.png](http://compass-keyboard.googlecode.com/files/global_swipe_horizontal.png)

## Függőleges ##
![http://compass-keyboard.googlecode.com/files/global_swipe_vertical.png](http://compass-keyboard.googlecode.com/files/global_swipe_vertical.png)

## Átlós ##
![http://compass-keyboard.googlecode.com/files/global_swipe_diagonal.png](http://compass-keyboard.googlecode.com/files/global_swipe_diagonal.png)

## Hosszú érintés ##
Bár a beépített kiosztások nem használják, van egy kilencedik módosító is: a hosszú érintés. Ennek kiválasztásához (pl. egy egyedileg hozzáadott kiosztás esetére) a billentyűzetet érintsük meg bárhol és tartsuk így 1.5 másodpercig.

## Latin-alapú ábécéknél ##
| **Ékezetes mód** | **Ékezet** |     | **Egyebek** |
|:-----------------|:-----------|:----|:------------|
| ÉNy              | balra dőlő      | ù   |             |
| É                | kalap           | û   | kettős ékezet |
| ÉK               | jobbra dőlő     | ú   |             |
| Ny               | kettőspont      | ü   |             |
| K                | egyéb           | ū   | pont, karika, felülvonás, speciális |
| DNy              | hullám          | ũ   |             |
| D                | ékjel           | ǔ   |             |
| DK               | speciális       | ų   | alsó vesszők, speciális |

## Cirill-alapú ábécéknél ##
| **Ékezetes mód** | **Ékezet** |     | **Egyebek** |
|:-----------------|:-----------|:----|:------------|
| ÉNy              | variáns         | ы   |             |
| ÉK               | lágy            | й   |             |
| DNy              | ékjel           | ӂ   | speciális   |
| DK               | speciális       | ӣ   | speciális   |

## Görög ábécénél ##
| **Ékezetes mód** | **Ékezet** |     | **Egyebek** |
|:-----------------|:-----------|:----|:------------|
| DK               | speciális       | ς   |             |

Még az ékezetes módok használatával is előfordul, hogy némely betűnek több variánsa van, mint ahány ékezetes módunk van, így ezen esetekben a ritkábban használatos variánsok valamely szomszédos betű egyébként nem használt ékezetes módjaihoz lettek rendelve, pl. az 'ő'-t ('o' dupla ékezettel) az 'n'-kalap helyére kellett helyezni, ami szomszédos az 'o'-val. Ez első pillantásra zavarónak tűnhet, de a tapasztalatok szerint igen gyorsan megszokottá válik.


# Speciális karakterek #
A leggyakrabban használatos ábécén kívüli írásjelek az összes elrendezés alap módjában érhetőek el. Ezek legtöbbje a rajzáról azonnal felismerhető, de mivel nem mindegyikükhöz kapcsolódik megjelenő karakter, így ezekhez az alábbi ábrák/jelek mutatják:

| **Billentyű**    | **Jel** | **Elhelyezkedés** |
|:-----------------|:--------|:------------------|
| Shift            | ⇧       | a bal-alsó billentyűn |
| Ctrl             | C       |                   |
| Caps lock        | ⇪       | a bal-alsó billentyűn 'Shift' módban |
| Elrejtés         | H       |                   |
| Esc              | E       | a bal-alsó billentyűn 'Spec' módban |
| Irány-billentyűk | ↑ ← → ↓ | a navigációs gombon |
| Enter            | ↲       |                   |
| Page Up/Down     | ↟ ↡     | a navigációs gombon 'Spec' módban |
| Home/End         | ↞ ↠     |                   |
| Center           | ╳       |                   |
| Backspace        | ↚       | az alsó-középső gombon |
| Space            | ▆       |                   |
| Tab              | ⇨       |                   |
| Del              | ↛       | az alsó-középső gombon 'Spec' módban |
| Ins              | ↱       |                   |
| F1..F10          | Ⅰ..Ⅹ    | a számoknál 'Spec' módban |
| 0..5 kiosztások  | L0..L5  | a bal-felső billentyűn 'Spec' módban |

# Rezgés #
Vannak, akik hasznosnak találják, ha érzékelhető visszajelzést kapnak a billentyűzet eseményeiről, így beállítható, hogy egy karakter bevitelekor, egy ékezetes mód kiválasztásakor illetve abból való visszalépéskor kérünk-e rezgő jelzést és ha igen, akkor egy- vagy kétszereset.

# Látható visszajelzés #
Igény esetén választható az épp bevitel alatt álló szimbólumról látható visszajelzés is, kétféle változatban (a 'semmilyen' mellett), külön-külön a sima szövegekhez és a jelszavak beviteléhez:

## Toast ##
Ebben a módban az épp kiválasztott szimbólum a képernyő tetejének közepén jelenik meg egy lebegő keretben, ami rövid időn belül vagy legkésőbb a karakter bevitelekor eltűnik.

## Kiemelés ##
Kevésbé látványos, mint az előző (ezért jelszóbevitelhez alkalmasabb), ebben a módban az épp kiválasztott szimbólum csak a gombon magán jelenik meg, viszont egy kissé nagyobb betűmérettel és más színnel.

# Méretezés #

Főképp a szélső billentyűk esetében zavaró lehet, hogy a képernyő pereme miatt a kifelé irányuló mozdulatok nehezen vihetőek be, ezért a beállítások között módunkban áll a bal- és jobboldali valamint az alsó margó szélességét beállítani.

Ehhez hasonlóan főképp nagy kijelzőjű készülékeknél zavaró lehet a billentyűk maximális méretének a lekorlátozása, így ezen felső korlátot is itt állíthatjuk be.


# Felhasználói elrendezések #
Annak ellenére, hogy a beépített elrendezések a legjobb odafigyeléssel lettek kialakítva, hogy a lehető legrugalmasabbak legyenek, előfordulhat, hogy egy másfajta elrendezést kézreállóbbnak találna, ezért a CompassKeyboard lehetővé teszi az elrendezésnek egy sima XML file-ból való betöltését is. Bár ez a lehetőség teljeskörű befolyást ad az elrendezések fölött, egy ilyen file elkészítése már valamelyest fejlesztői feladat és mindenképpen egy időigényes és fárasztó folyamat, úgyhogy mielőtt ebbe belevágna, kérem mindenképpen mérlegelje először, hogy megéri-e az erre fordítandó idejét és fáradságát, valamint érdemes először átnézni a forráskódban található LayoutFormat.README leírást és a beépített elrendezések XML file-jait.

![http://compass-keyboard.googlecode.com/files/custom_filebrowser.png](http://compass-keyboard.googlecode.com/files/custom_filebrowser.png)


# Karakterkészletek #
Nem minden betű-jel van támogatva minden telefonon, így előfordulhat, hogy valamely nemzeti karakter helyén csak egy helykitöltő négyzet jelenik meg (ez a helyzet pl. a Galaxy S-en néhány ékjeles nagybetű esetében).

Annak ellenőrzésére, hogy a telefonja mely karakter-jeleket támogatja, látogassa meg a böngészőjével a kódtáblázatokat a [Unicode Basic Multilingual Plane](http://en.wikipedia.org/wiki/Basic_Multilingual_Plane#Basic_Multilingual_Plane) oldalon, és ellenőrizze, hogy a kérdéses jel rendesen megjelenik-e.

Bár a billentyű-elrendezésekben bármely Unicode karakter elhelyezhető lenne, nem minden ábécéhez van tényleges támogatás a telefonokon, ezért jelenleg az ilyeneket (pl. az örmény és a grúz betűket) az elrendezések sem támogatják.


# További fejlesztési ötletek #

## Keleti nyelvek, pl. a japán és a kínai ##
Mivel az írási rendszerük teljesen különböző az betű-írásos nyelvekétől, a legjobb, amit el tudnék gondolni, az egy beviteli terület, ahol a felhasználó elkezdheti behúzni a kívánt szótag-jel (egyszerűsített) vonalait, és eközben a háttérben megjelennének azon jelek, amelyek az eddig bevitt vonalakra illeszthetőek, így a felhasználó vagy egyre szűkíthetné a lehetőségeket újabb és újabb vonalakkal, vagy kiválaszthatá a kívánt jelet, amennyiben az már megjelent a háttérben.

## Az arab és a perzsa ##
Ezek a nyelvek jobbról balra írnak, nem tesznek különbséget kis- és nagybetűk között, és a magánhangzókat csak mellékjelek formájában jelölik, viszont különböző betűalakokat használnak a szavak elején, közepén, végén ill. ha a betű magában áll.
Az Android keretrendszere lehetővé tenné a környezetfüggő viselkedést, így még egy ilyen billentyűzet is megvalósítható lenne.


# Licensz, ár, visszajelzések #
A CompassKeyboard FreeBSD-stílusú licensz alatt lett kiadva, úgyhogy szabadon terjeszthető és használható eredeti és módosított formában egyaránt mindaddig, amíg a szerzői jogra vonatkozó megjegyzésem nincs eltávolítva belőle.

Teljes mértékben ingyenes, mindazonáltal ha támogatni kívánja a további fejlesztést és használhatósága alapján  a CompassKeyboard-ot érdemesnek találja egy pár dollár értékére, a hozzájárulását köszönettel veszem a PayPal-on keresztül: 'gabor.simon@mailbox.hu'.

Ha van valamely elgondolása, amivel a CompassKeyboard-ot tovább lehetne fejleszteni, vagy eszébe jut valamely olyan tulajdonság, amit hasznosnak találna, kérem ossza meg velem a fentebbi email-címemen. Mivel ez a fejlesztés számomra csak egy hobbi, nem tehetek ígéreteket ezzel kapcsolatban, de legalább a legnépszerűbb elgondolásokat meg fogom próbálni megvalósítani.


# Köszönet #
A fejlesztést segítő hasznos elgondolásokért és javaslatokért szeretnék köszönetet mondani Christian Marg-nak, valamint a folyamatos támogatásért és bíztatásért a családomnak, barátaimnak, tanáraimnak és munkatársaimnak, és mindenek felett a Nagy Főnöknek Mindenek Felett :).