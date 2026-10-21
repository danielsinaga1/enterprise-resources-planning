# 📋 DAFTAR USER DEMO LENGKAP - IRMS (KPI)

> **⚠️ CATATAN PASSWORD**  
> Semua password perlu direset menggunakan hash BCrypt yang kompatibel dengan Spring Security.  
> Jalankan perintah berikut via MySQL CLI (gunakan file SQL untuk menghindari masalah escape):
>
> **Buat file `reset_password.sql`** dengan isi:
> ```sql
> UPDATE users SET status = 1;
> UPDATE users SET password = '$2a$10$X2hPNVxKTcBNZrhIWjhe..6dPhQTa3rISSB8Op2LETgl24x9r/ngO';
> ```
>
> **Jalankan:**
> ```bash
> mysql -u root db_nirms < reset_password.sql
> ```
>
> Setelah reset: **Password semua akun = `password`**

---

## 📊 RINGKASAN ROLE SISTEM

| ID | Role | Keterangan |
|----|------|------------|
| 1 | Admin | Full akses sistem |
| 2 | User | User biasa |
| 3 | Super Administrator | Administrator tertinggi |
| 4 | Approval 1 | Superintendent / persetujuan level 1 |
| 5 | Approval 2 | Manager / persetujuan level 2 |
| 6 | General Manager | General Manager |
| 7 | Initiator | Pembuat laporan insiden / observasi |
| 8 | Administrator | Administrator umum |
| 9 | Vice President | Vice President |
| 10 | Administrator Safety | Admin khusus keselamatan |
| 11 | Leader JSO | Ketua JSO (Job Safety Observation) |
| 12 | Member JSO | Anggota JSO |

---

## 1. ADMIN & SUPER ADMINISTRATOR

| No | ID | NPK | Nama | Email | Role |
|----|----|-----|------|-------|------|
| 1 | 1 | 1 | Admin | admin@admin.com | Admin |
| 2 | 165 | 2002196 | Administrator Safety | daniel.sinagaaaa@kpi.co.id | Admin |
| 3 | 40 | 1001248 | Muhammad Rosyad | muhammad.rosyad@kpi.co.id | Super Administrator |

---

## 2. APPROVAL 1 (SUPERINTENDENT)

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 56 | 1302260 | Adi Hendri Setiawan | adi.setiawan@kpi.co.id |
| 2 | 24 | 0101110 | Aguspar | aguspar@kpi.co.id |
| 3 | 163 | 1102251 | Baskara Aji Nugraha | baskara.nugraha@kpi.co.id |
| 4 | 119 | 0002046 | Burhanuddin | burhanuddin@kpi.co.id |
| 5 | 150 | 1501321 | Filoyak Josua Sinaga | filoyak.sinaga@kpi.co.id |
| 6 | 138 | 1501305 | Finqo Aprianto | finqo.aprianto@kpi.co.id |
| 7 | 141 | 1302286 | Gihon Andre Asmitra Harahap | gihon.harahap@kpi.co.id |
| 8 | 184 | 2301406 | Heri Susanto | heri.susanto@kpi.co.id |
| 9 | 106 | 0001071 | Herry Sulistiono | herry.sulistiono@kpi.co.id |
| 10 | 108 | 0001076 | Joko Susilo | joko.susilo@kpi.co.id |
| 11 | 180 | 2102382 | Julpian Tulus Simanjuntak | julpian.simanjuntak@kpi.co.id |
| 12 | 50 | 0301178 | Lumban MT Sinaga | lumban.sinaga@kpi.co.id |
| 13 | 7 | 0101102 | Masniari Nasution | masniari.nasution@kpi.co.id |
| 14 | 66 | 0001024 | Masrani | masrani@kpi.co.id |
| 15 | 139 | 1502320 | Mochamad Wildan | mochamad.wildan@kpi.co.id |
| 16 | 5 | 0101123 | Mohamad Ahman | mohamad.ahman@kpi.co.id |
| 17 | 187 | 2502427 | Mohammad Amrih Raharjo | mohammad.raharjo@kpi.co.id |
| 18 | 40 | 1001248 | Muhammad Rosyad | muhammad.rosyad@kpi.co.id |
| 19 | 140 | 1302282 | Rendy Vapriandani | rendy.vapriandani@kpi.co.id |
| 20 | 164 | 2002195 | Superintendent Executor | superintendenttest241@gmail.com |
| 21 | 159 | 2002191 | Superintendent Test Creator | alfiansyah.damaraa@kpi.co.id |
| 22 | 172 | 0001027 | Wahyono Adhi | wahyono.adhi@kpi.co.id |
| 23 | 94 | 1001250 | Wijianto | wijianto@kpi.co.id |

---

## 3. APPROVAL 2 (MANAGER)

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 133 | 0801226 | Ardyan Kusuma Jaya | ardyan.kusuma@kpi.co.id |
| 2 | 169 | 0802209 | Ashabul Munir | ashabul.munir@kpi.co.id |
| 3 | 3 | 9502005 | Budi Handoyo | budi.handoyo@kpi.co.id |
| 4 | 120 | 0802219 | Danny Anggara | danny.anggara@kpi.co.id |
| 5 | 28 | 0001038 | Darwis Tolleng | darwis.tolleng@kpi.co.id |
| 6 | 8 | 0002053 | Eddy Mardiawan | eddy.mardiwan@kpi.co.id |
| 7 | 14 | 0601194 | Faisal Fachmi | faisal.fachmi@kpi.co.id |
| 8 | 158 | 2002190 | Manager Creator Test | daniel.sinagaaaaaaa@kpi.co.id |
| 9 | 161 | 2002193 | Manager Executor Test | managertest94@gmail.com |
| 10 | 22 | 0102157 | Muhammad Nasrudin | nasrudin@kpi.co.id |
| 11 | 58 | 0902229 | Murra Candra Wicaksana | murra.candra@kpi.co.id |
| 12 | 45 | 1702350 | R Tommy Hendrawanto | tommy.hendrawanto@kpi.co.id |
| 13 | 10 | 1602333 | Reynold PM Marbun | reynold.marbun@kpi.co.id |
| 14 | 173 | 1801365 | Sarono F A | sarono@kpi.co.id |
| 15 | 11 | 1702348 | Stevanus Yandi Putra Aji | stevanus.yandi@kpi.co.id |
| 16 | 166 | 1901376 | Suyanto | suyanto@kpi.co.id |
| 17 | 96 | 0802214 | Zaki Sani Rahman | zaki.rahman@kpi.co.id |

---

## 4. GENERAL MANAGER

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 12 | 1602336 | David Desmanto Simbolon | gonzalez2002@gmail.com |
| 2 | 144 | 1702341 | Kus Heryadi | kus.heryadi@kpi.co.id |
| 3 | 136 | 9203270 | Sapto Hari Prasetyo | sapto.hari@kpi.co.id |

---

## 5. INITIATOR

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 91 | 0701207 | Abdul Rahman | abdul.rahman@kpi.co.id |
| 2 | 182 | 2101385 | Adinolfi Pasaribu | adinolfi.pasaribu@kpi.co.id |
| 3 | 54 | 0901233 | Agus Jaya | agus.jaya@kpi.co.id |
| 4 | 98 | 0001055 | Agus Supriadi | agus.supriadi@kpi.co.id |
| 5 | 34 | 0602195 | Agustinus Sad S | agustinus.sad@kpi.co.id |
| 6 | 74 | 0101105 | Ahmad Syaifuddin | ahmad.syaifuddin@kpi.co.id |
| 7 | 176 | 0201168 | Akhmad Sayuti G | akhmad.sayuti@kpi.co.id |
| 8 | 17 | 0101130 | Alfian | alfian@kpi.co.id |
| 9 | 43 | 1901370 | Alfiansyah Damara | alfiansyah.damara@kpi.co.id |
| 10 | 86 | 1802353 | Andre Febrianto | andre.febrianto@kpi.co.id |
| 11 | 88 | 1501314 | Angga Galuh Pradana | angga.pradana@kpi.co.id |
| 12 | 99 | 0001057 | Ansyursyah | ansyursyah@kpi.co.id |
| 13 | 20 | 1701342 | Antonio Nalau | antonio.nalau@kpi.co.id |
| 14 | 177 | 0101131 | Ardiandy | ardiandy@kpi.co.id |
| 15 | 53 | 0001032 | Arifah Priestiwi | arifah.priestiwi@kpi.co.id |
| 16 | 121 | 0001042 | Aryani Retno S | aryani.retno@kpi.co.id |
| 17 | 152 | 0101121 | Asri Kamal | asri.kamal@kpi.co.id |
| 18 | 61 | 1802355 | Avila Dhanu Kurniawan | avila.kurniawan@kpi.co.id |
| 19 | 59 | 0001058 | Bagus Yuwono | bagus.yuwono@kpi.co.id |
| 20 | 75 | 0101133 | Bambang Sutrisno | bambang.sutrisno@kpi.co.id |
| 21 | 67 | 0001045 | Bistok Lumban Raja | bistok.lumban@kpi.co.id |
| 22 | 160 | 2002192 | Creator Test | daniellegend74@gmail.com |
| 23 | 2 | 1902379 | Daniel Sinaga | daniel.sinaga@kpi.co.id |
| 24 | 78 | 0101153 | Deddy Irawan | deddy.irawan@kpi.co.id |
| 25 | 100 | 0001062 | Dedie Noeryanto | dedie.noeryanto@kpi.co.id |
| 26 | 4 | 0801220 | Dewi Kilianjani P | dewi.kilianjani@kpi.co.id |
| 27 | 101 | 0001063 | Dharlys Tiku Kala | dharlys.kala@kpi.co.id |
| 28 | 143 | 1502329 | Dicky Indra Saputra | dicky.saputra@kpi.co.id |
| 29 | 87 | 1802360 | Dimas Dwi Ananda | dimas.ananda@kpi.co.id |
| 30 | 81 | 0101165 | Djono | djono@kpi.co.id |
| 31 | 102 | 0001064 | Don Ericson Panjaitan | don.erricson@kpi.co.id |
| 32 | 103 | 0001065 | Dwi Kusharyono | dwi.kusharyono@kpi.co.id |
| 33 | 38 | 0402181 | Dwi Putranti Ratna Dewi | dwi.putranti@kpi.co.id |
| 34 | 79 | 0101155 | Eddy Dwiono Sakti | eddy.dwiono@kpi.co.id |
| 35 | 48 | 0301174 | Edi Suranta Tarigan | edi.suranta@kpi.co.id |
| 36 | 49 | 0301177 | Edi Susanto | edi.susanto@kpi.co.id |
| 37 | 104 | 0001066 | Eko Sudrajat | eko.sudrajat@kpi.co.id |
| 38 | 132 | 0102124 | Endik Warsito | endik.warsito@kpi.co.id |
| 39 | 93 | 1701343 | Ery Achmad Rondhi | ery.rondhi@kpi.co.id |
| 40 | 162 | 2002194 | Executor Test | executortest1@gmail.com |
| 41 | 125 | 1401296 | Fajar Adiyatama | fajar.adiyatama@kpi.co.id |
| 42 | 105 | 0001068 | Fery Yon | fery.yon@kpi.co.id |
| 43 | 85 | 1802356 | Gilang Hari Pratomo | gilang.pratomo@kpi.co.id |
| 44 | 55 | 0401180 | Greweng Arieanto | greweng.arieanto@kpi.co.id |
| 45 | 36 | 0801224 | Hamzah Yahya | hamzah@kpi.co.id |
| 46 | 46 | 0001036 | Hariyanto | hariyanto.wh@kpi.co.id |
| 47 | 18 | 0101129 | Harun A. Rosyid | harun.arosyid@kpi.co.id |
| 48 | 175 | 0501193 | Heriman Amri Lubis | heriman.lubis@kpi.co.id |
| 49 | 15 | 1201256 | Hestiningrum | hestiningrum@kpi.co.id |
| 50 | 60 | 1802354 | Ilham Adityarsena Febryantho | ilham.adityarsena@kpi.co.id |
| 51 | 95 | 1401295 | Ina Orrania Jayanti Pattaru | ina.orrania@kpi.co.id |
| 52 | 31 | 0101122 | Isma Rahmawaty | isma.rahmawaty@kpi.co.id |
| 53 | 107 | 0001074 | Iwan Surono | iwan.surono@kpi.co.id |
| 54 | 33 | 0502184 | Joko Harmanto | joko.harmanto@kpi.co.id |
| 55 | 35 | 0001040 | Kairul | kairul@kpi.co.id |
| 56 | 109 | 0001077 | Khamim Rosidi | khamim.rosidi@kpi.co.id |
| 57 | 9 | 1502331 | Laura Theresia | laura.theresia@kpi.co.id |
| 58 | 6 | 0101104 | Lelly Madona | lelly.madona@kpi.co.id |
| 59 | 68 | 0001080 | Lewi Tulak | lewi.tulak@kpi.co.id |
| 60 | 129 | 0701200 | Lukman Cahaya Mustika | lukman.cahaya@kpi.co.id |
| 61 | 148 | 1501311 | M Delta Aslam Azis | muhammad.delta@kpi.co.id |
| 62 | 147 | 1301270 | M Wahid Ardani B | muhammad.wahid@kpi.co.id |
| 63 | 117 | 0001034 | Mardiyanto | mardiyanto@kpi.co.id |
| 64 | 178 | 1502317 | Maro Diapri Fernando Simbolon | fernando.simbolon@kpi.co.id |
| 65 | 186 | 2502426 | Merisa Veronika Suparto | merisa.suparto@kpi.co.id |
| 66 | 26 | 0001033 | Mery Juna Manalu | meryjuna.manalu@kpi.co.id |
| 67 | 183 | 2101386 | Muhammad Bima Suwardani | muhammad.suwardani@kpi.co.id |
| 68 | 63 | 1802358 | Muhammad Hanif Ramadhan | muhamad.ramadhan@kpi.co.id |
| 69 | 25 | 9602009 | Muhammad Ismail | muhammad.ismail@kpi.co.id |
| 70 | 77 | 0101152 | Muhammad Nur | muhammad.nur@kpi.co.id |
| 71 | 110 | 0001081 | Muhammad Nur Faisal | muhammad.faisal@kpi.co.id |
| 72 | 134 | 0902232 | Muhammad Rizki | muhammad.rizki@kpi.co.id |
| 73 | 145 | 1701347 | Muhammad Septian | muhammad.septian@kpi.co.id |
| 74 | 44 | 1901371 | Muhammad Yusuf | muhammad.yusuff@kpi.co.id |
| 75 | 122 | 0102140 | Nandang Kurniawan | nandang.kurniawan@kpi.co.id |
| 76 | 29 | 0001039 | Niko Dimus | niko.dimus@kpi.co.id |
| 77 | 41 | 1302287 | Nugroho Cahyo Riadmojo | cahyo.riadmojo@kpi.co.id |
| 78 | 69 | 0001083 | Nursyain | nursyain@kpi.co.id |
| 79 | 128 | 0101135 | Pahmi | pahmi@kpi.co.id |
| 80 | 111 | 0001084 | Parno | parno@kpi.co.id |
| 81 | 92 | 0101127 | Rahmat Kartolo | rahmat.kartolo@kpi.co.id |
| 82 | 84 | 1302284 | Randika Gunawan | randika.gunawan@kpi.co.id |
| 83 | 112 | 0001086 | Rindu Firmansyah | rindu.firmansyah@kpi.co.id |
| 84 | 131 | 0701204 | Risky Ilhamsyah | risky.ilhamsyah@kpi.co.id |
| 85 | 70 | 0001088 | Ronald Tampubolon | ronald.tampubolon@kpi.co.id |
| 86 | 21 | 1901372 | Rose Diana Rahel Sianturi | rose.sianturi@kpi.co.id |
| 87 | 146 | 0102141 | Rully Andrian Nugroho | rully.nugroho@kpi.co.id |
| 88 | 127 | 0101126 | Ruslan | ruslan@kpi.co.id |
| 89 | 76 | 0101149 | Said Alamsyah | said.alamsyah@kpi.co.id |
| 90 | 23 | 9601007 | Slamet Untung | slamet.untung@kpi.co.id |
| 91 | 39 | 1902367 | Stephanie Halim | stephanie.halim@kpi.co.id |
| 92 | 71 | 0001091 | Sufian Nur | sufian.nur@kpi.co.id |
| 93 | 72 | 0001092 | Sugiman | sugiman@kpi.co.id |
| 94 | 157 | 0001093 | Sukadi | sukadi@kpi.co.id |
| 95 | 82 | 0401182 | Sunaryo | sunaryo@kpi.co.id |
| 96 | 27 | 0001037 | Suprasetyo | suprasetyo@kpi.co.id |
| 97 | 19 | 0801218 | Susana Afriati | susana.afriati@kpi.co.id |
| 98 | 137 | 1102252 | Syahrul Sanudji | syahrul.sanudji@kpi.co.id |
| 99 | 32 | 0101128 | Syarifuddin | syariffuddin@kpi.co.id |
| 100 | 30 | 0101106 | Tabed Abia B. | tabed@kpi.co.id |
| 101 | 126 | 0101138 | Takalimin | takalimin@kpi.co.id |
| 102 | 16 | 1401293 | Theodora Natalia CS | theodora.nathalia@kpi.co.id |
| 103 | 89 | 0101107 | Tumpal Pandapotan Siregar | tumpal.siregar@kpi.co.id |
| 104 | 113 | 0001094 | Tuwuh Sudiharto | tuwuh.sudiharto@kpi.co.id |
| 105 | 114 | 0001095 | Wachid | wachid@kpi.co.id |
| 106 | 130 | 0701202 | Wachyudi | wachyudi@kpi.co.id |
| 107 | 116 | 0501191 | Wahyu | wahyu.opr@kpi.co.id |
| 108 | 118 | 0001100 | Wahyu | wahyu@kpi.co.id |
| 109 | 90 | 0101139 | Wahyu Purwono | wahyu.purwono@kpi.co.id |
| 110 | 115 | 0001096 | Wahyu Teguh Setyadi | wahyu.teguh@kpi.co.id |
| 111 | 47 | 0101114 | Widi Pahala | widi.pahala@kpi.co.id |
| 112 | 73 | 0001098 | Winarno | winarno@kpi.co.id |
| 113 | 142 | 1502328 | Yehezkiel Imanuel Maruanaya | yehezkiel.maruanaya@kpi.co.id |
| 114 | 37 | 1502313 | Yehuda Yuda | yehuda@kpi.co.id |
| 115 | 62 | 1802357 | Yogi Anastra Danu Wijaya | yogi.wijaya@kpi.co.id |
| 116 | 51 | 0501188 | Yudiansyah | yudiansyah@kpi.co.id |
| 117 | 149 | 1402290 | Yurisman Djamaluddin | yurisman.djamaluddin@kpi.co.id |
| 118 | 52 | 0101113 | Zainul Arifin | zainul.arifin@kpi.co.id |
| 119 | 80 | 0101164 | Zuhadi | zuhadi@kpi.co.id |

---

## 6. VICE PRESIDENT

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 13 | 9902014 | B. Budi Hermawan | budi.hermawan@kpi.co.id |
| 2 | 171 | 0102101 | Erwin Firyadi | erwin.firyadi@kpi.co.id |
| 3 | 170 | BOD002 | Hotdo M Pasaribu | hotdo.pasaribu@kpi.co.id |
| 4 | 168 | 0401179 | Pradhika Rama Ulianto | pradhika.ulianto@kpi.co.id |
| 5 | 135 | 1902368 | Robertus Gunung Dwiriawan | robertus.dwiriawan@kpi.co.id |
| 6 | 185 | 2201396 | Eman Sonda | eman.sonda@kpi.co.id |

---

## 7. ADMINISTRATOR SAFETY

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 186 | 2502426 | Merisa Veronika Suparto | merisa.suparto@kpi.co.id |
| 2 | 181 | 2101387 | Rahmatullah Qarib Gobel | rahmatullah.gobel@kpi.co.id |
| 3 | 88 | 1501314 | Angga Galuh Pradana | angga.pradana@kpi.co.id |

---

## 8. LEADER JSO

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 169 | 0802209 | Ashabul Munir | ashabul.munir@kpi.co.id |
| 2 | 13 | 9902014 | B. Budi Hermawan | budi.hermawan@kpi.co.id |
| 3 | 163 | 1102251 | Baskara Aji Nugraha | baskara.nugraha@kpi.co.id |
| 4 | 120 | 0802219 | Danny Anggara | danny.anggara@kpi.co.id |
| 5 | 171 | 0102101 | Erwin Firyadi | erwin.firyadi@kpi.co.id |
| 6 | 14 | 0601194 | Faisal Fachmi | faisal.fachmi@kpi.co.id |
| 7 | 138 | 1501305 | Finqo Aprianto | finqo.aprianto@kpi.co.id |
| 8 | 187 | 2502427 | Mohammad Amrih Raharjo | mohammad.raharjo@kpi.co.id |
| 9 | 22 | 0102157 | Muhammad Nasrudin | nasrudin@kpi.co.id |
| 10 | 58 | 0902229 | Murra Candra Wicaksana | murra.candra@kpi.co.id |
| 11 | 122 | 0102140 | Nandang Kurniawan | nandang.kurniawan@kpi.co.id |
| 12 | 168 | 0401179 | Pradhika Rama Ulianto | pradhika.ulianto@kpi.co.id |
| 13 | 45 | 1702350 | R Tommy Hendrawanto | tommy.hendrawanto@kpi.co.id |
| 14 | 173 | 1801365 | Sarono F A | sarono@kpi.co.id |
| 15 | 172 | 0001027 | Wahyono Adhi | wahyono.adhi@kpi.co.id |
| 16 | 96 | 0802214 | Zaki Sani Rahman | zaki.rahman@kpi.co.id |

---

## 9. MEMBER JSO

| No | ID | NPK | Nama | Email |
|----|----|-----|------|-------|
| 1 | 56 | 1302260 | Adi Hendri Setiawan | adi.setiawan@kpi.co.id |
| 2 | 182 | 2101385 | Adinolfi Pasaribu | adinolfi.pasaribu@kpi.co.id |
| 3 | 24 | 0101110 | Aguspar | aguspar@kpi.co.id |
| 4 | 86 | 1802353 | Andre Febrianto | andre.febrianto@kpi.co.id |
| 5 | 133 | 0801226 | Ardyan Kusuma Jaya | ardyan.kusuma@kpi.co.id |
| 6 | 61 | 1802355 | Avila Dhanu Kurniawan | avila.kurniawan@kpi.co.id |
| 7 | 160 | 2002192 | Creator Test | daniellegend74@gmail.com |
| 8 | 78 | 0101153 | Deddy Irawan | deddy.irawan@kpi.co.id |
| 9 | 101 | 0001063 | Dharlys Tiku Kala | dharlys.kala@kpi.co.id |
| 10 | 87 | 1802360 | Dimas Dwi Ananda | dimas.ananda@kpi.co.id |

---

## 🔐 QUICK REFERENCE - CREDITS LOGIN DEMO

> Setelah menjalankan SQL reset password di atas, semua akun dapat diakses dengan password `password`.

### Akun Kunci untuk Demo

| Role | Nama | Email | Fungsi dalam Demo |
|------|------|-------|-------------------|
| Admin | Admin | admin@admin.com | Full akses sistem |
| Super Administrator | Muhammad Rosyad | muhammad.rosyad@kpi.co.id | Administrasi & konfigurasi |
| Initiator | Daniel Sinaga | daniel.sinaga@kpi.co.id | Buat laporan insiden / observasi |
| Approval 1 (Superintendent) | Aguspar | aguspar@kpi.co.id | Setujui laporan (level 1) |
| Approval 2 (Manager) | Budi Handoyo | budi.handoyo@kpi.co.id | Setujui laporan (level 2) |
| General Manager | Kus Heryadi | kus.heryadi@kpi.co.id | Approval GM |
| Vice President | Hotdo M Pasaribu | hotdo.pasaribu@kpi.co.id | Approval VP / tertinggi |
| Administrator Safety | Rahmatullah Qarib Gobel | rahmatullah.gobel@kpi.co.id | Kelola data keselamatan |
| Leader JSO | Muhammad Nasrudin | nasrudin@kpi.co.id | Pimpin observasi JSO |
| Member JSO | Aguspar | aguspar@kpi.co.id | Ikut observasi JSO |

---

## 🎯 SKENARIO DEMO

### Demo 1: Alur Laporan Insiden (Incident Report)
```
1. Login sebagai Initiator: daniel.sinaga@kpi.co.id
2. Buat laporan insiden baru
3. Submit laporan

4. Login sebagai Approval 1 (Superintendent): aguspar@kpi.co.id
5. Review dan setujui laporan (Approval 1)

6. Login sebagai Approval 2 (Manager): budi.handoyo@kpi.co.id
7. Review dan setujui laporan (Approval 2)

8. Login sebagai Admin: admin@admin.com
9. Lihat dashboard & rekap semua laporan
```

### Demo 2: Alur Safety Observation
```
1. Login sebagai Initiator: daniel.sinaga@kpi.co.id
2. Buat laporan observasi keselamatan (safety observation)
3. Submit

4. Login sebagai Approval 1: aguspar@kpi.co.id
5. Review dan setujui

6. Login sebagai Administrator Safety: rahmatullah.gobel@kpi.co.id
7. Kelola data keselamatan / follow-up
```

### Demo 3: Alur Job Safety Observation (JSO)
```
1. Login sebagai Leader JSO: nasrudin@kpi.co.id
2. Buat jadwal / form JSO baru

3. Login sebagai Member JSO: aguspar@kpi.co.id
4. Isi form observasi lapangan

5. Login sebagai Leader JSO: nasrudin@kpi.co.id
6. Review dan tutup observasi

7. Login sebagai Admin: admin@admin.com
8. Lihat recap JSO di dashboard
```

### Demo 4: Multi-Level Approval (Full Flow)
```
Initiator      → buat laporan
Approval 1     → setujui (Superintendent)
Approval 2     → setujui (Manager)
General Manager → (jika diperlukan)
Vice President  → approval tertinggi
```

---

## 🗄️ QUERY DATABASE BERGUNA

```sql
-- Lihat semua user aktif beserta role
SELECT u.id, u.name, u.email, u.npk, r.title as role
FROM users u
LEFT JOIN role_user ru ON u.id = ru.user_id
LEFT JOIN roles r ON ru.role_id = r.id
WHERE u.deleted_at IS NULL
ORDER BY r.id, u.name;

-- Reset semua password ke 'password' untuk demo
UPDATE users SET password = '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi';

-- Lihat semua role
SELECT id, title FROM roles ORDER BY id;

-- Cek jumlah user per role
SELECT r.title, COUNT(ru.user_id) as jumlah_user
FROM roles r
LEFT JOIN role_user ru ON r.id = ru.role_id
GROUP BY r.id, r.title
ORDER BY r.id;
```

---

## ⚠️ CATATAN PENTING

1. **Domain email**: Semua user memakai domain `@kpi.co.id`
2. **NPK**: Nomor Pokok Karyawan, format variatif sesuai data asli KPI
3. **Password**: Harus di-reset dulu menggunakan SQL di atas sebelum demo
4. **Multi-role**: Beberapa user memiliki lebih dari satu role (misal: beberapa user adalah Approval 1 sekaligus Leader JSO)
5. **User test**: Beberapa akun adalah akun test (email Gmail / domain berbeda) — dibuat saat development


---

## 🔄 11. UPDATE HISTORY

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2026-01-12 | 1.0 | Initial comprehensive user list | System |
| 2026-01-12 | 1.1 | Added troubleshooting section & fix scripts | System |
| 2026-01-13 | 1.3 | Fixed employee data requirements, Added comprehensive troubleshooting | System |

---

**© 2026 IRMS - PT Kaltim Parna industri**

*Dokumen ini adalah referensi lengkap untuk demo dan testing IRMS*
