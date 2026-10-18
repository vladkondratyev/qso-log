<h1>
<picture>
<source media="(prefers-color-scheme: dark)" srcset="docs/logo-dark.png">
<img src="docs/logo-light.png" alt="QSO-LOG" width="340">
</picture>
</h1>

[Русский](README.md) · **English**

QSO-LOG is a logbook for radio amateurs. It runs on Android, macOS, Windows and Linux, and does the same things
on the phone and on the computer.

A QSO takes a few taps: callsign, frequency, RST. The frequency, band and mode can come from the transceiver over CAT
(ICOM CI-V, Kenwood, Elecraft, Yaesu, Hamlib rigctld), and the app tunes it from its own panel with meters and a band scale; CW messages — CQ,
callsigns, exchange numbers — go out from the F1–F8 keys; FT8 and FT4 QSOs come in by themselves from WSJT-X, JTDX
and MSHV. The app looks up the other station's name and city on QRZ.ru
or QRZ.com, works out the distance and draws the path on a map. You can attach a voice note to a QSO. Contest mode
gives fast entry with exchange numbers, and the log exports to Cabrillo and to ЕРМАК, the Russian contest format.
A dashboard shows the log in charts. The log exports to ADIF and CSV and uploads to LoTW, eQSL, Club Log and QRZ.com, and several devices can keep one log through
your own Google Sheet. Buttons and text are large, so you can log on the air; with an external keyboard you can log
without touching the screen at all.

The app is free and open source. The interface is in Russian and English.

<p align="center">
<img src="docs/screenshots/00-platforms.png" width="820" alt="QSO-LOG on a computer and a phone: the transceiver panel over CAT, CW from the F1–F8 keys, FT8 and FT4 QSOs from WSJT-X and JTDX">
</p>

## Download

| System | Version | File |
|---|---|---|
| Android 8.0 or newer | 2.6.0 | [QSO-LOG-2.6.0.apk](../../releases/tag/v2.6.0) |
| macOS 11 or newer, Apple silicon (M1 and newer) | 2.6.0 | [QSO-LOG-2.6.0-macos-arm64.dmg](../../releases/tag/desktop-v2.6.0) |
| macOS 11 or newer, Intel | 2.6.0 | [QSO-LOG-2.6.0-macos-x64.dmg](../../releases/tag/desktop-v2.6.0) |
| Windows 10 and 11, 64-bit | 2.6.0 | [QSO-LOG-2.6.0-windows-x64.zip](../../releases/tag/desktop-v2.6.0) |
| Linux: Ubuntu 22.04+, Debian 12, Mint | 2.6.0 | [QSO-LOG-2.6.0-linux-amd64.deb](../../releases/tag/desktop-v2.6.0) |

How to install: [Android](#android) · [macOS](#macos) · [Windows](#windows) · [Linux](#linux).
The [project page](https://vladkondratyev.github.io/qso-log/?lang=en) offers the right file for your system.

Release notes on GitHub are written in Russian. Most screenshots below show the Russian interface; the language
is switched in Settings and changes at once.

## Contents

- [First start](#first-start)
- [New QSO](#new-qso)
- [CAT — transceiver](#cat--transceiver)
- [CW from the F1–F8 keys](#cw-from-the-f1f8-keys)
- [Digital modes — WSJT-X, JTDX, MSHV](#digital-modes--wsjt-x-jtdx-mshv)
- [Contest mode](#contest-mode)
- [Keyboard only](#keyboard-only)
- [Log](#log)
- [Dashboard](#dashboard)
- [Search history](#search-history)
- [Station data](#station-data)
- [Voice notes](#voice-notes)
- [Distance and maps](#distance-and-maps)
- [Export and import](#export-and-import)
- [Online logbooks: LoTW, eQSL, Club Log, QRZ.com](#online-logbooks-lotw-eqsl-club-log-qrzcom)
- [Sync through a Google Sheet](#sync-through-a-google-sheet)
- [Reference and calculators](#reference-and-calculators)
- [Settings](#settings)
- [On the computer](#on-the-computer)
- [Installation](#installation)
- [Where the data is kept](#where-the-data-is-kept)
- [Known issues](#known-issues)
- [For developers](#for-developers)

## First start

The app asks two things: your callsign and where you are. Instead of a QTH locator you can type
a city, and the locator is found for you. Every step can be skipped and filled in later in Settings. QRZ.ru and QRZ.com
accounts (for the other station's name and town) are added later in Settings — the QSO card reminds you.

After an update the app shows once what is new in that version.

After that the "Add QSO" button appears at the bottom of the screen. Every QSO starts there.

<p>
<img src="docs/screenshots/en-01-log.png" width="200" alt="Log">
<img src="docs/screenshots/en-02-new-qso.png" width="200" alt="New QSO">
<img src="docs/screenshots/en-03-language.png" width="200" alt="Language">
</p>

## New QSO

The card opens with the cursor in the callsign field. The UTC date and time are set for you. Mode, band and frequency
come from the previous QSO, so usually only the callsign and RST are left.

What helps while typing:

- From the second letter, callsigns from your log appear under the field. Pick one instead of typing it all.
- From the fourth letter, the app looks the station up on QRZ.ru (and QRZ.com) and fills in name, city and locator.
- A yellow box shows how many times you have worked this callsign and when last. If you worked it on several bands,
  small marks like "40m 25.09.26 · 20m 07.10.26" show the last QSO on each band.
- A red "Dupe" box appears if you already worked the station today on the same band and mode. Handy in contests.
- Frequency can be typed in kHz: 14195 becomes 14.195 MHz, and the band is picked from it.
- For RST the digit keyboard opens, with buttons for common reports under the field: 59, 57, 599, −10 and so on.
- "+ Next" saves the QSO and opens a new card on the same frequency. Useful in a pile-up.

The other ADIF fields are in collapsible groups: station worked, QSO, my station, propagation. The QSO time can be
taken when the card opens or when it is saved; this is a setting. On the phone, with "Take the QSO time when the card
is saved" on, a clock with seconds runs in the new card, so the time does not freeze while a station is called in a
pile-up; a saved QSO always keeps its time from the log. ⌫ at the right of the callsign field (and of the log search)
clears it whole.

A saved QSO has three buttons at the bottom: delete, share (as text, with the voice note, to a messenger or e-mail)
and save.

<p>
<img src="docs/screenshots/22-call-suggest.png" width="200" alt="Callsign suggestions from the log">
<img src="docs/screenshots/23-dupe.png" width="200" alt="Dupe and worked before">
<img src="docs/screenshots/02-card.png" width="200" alt="Saved QSO">
<img src="docs/screenshots/55-keypad-compact.png" width="200" alt="Built-in compact keyboard in the card">
</p>

### Built-in keyboard

Instead of the system keyboard you can use the app's own, as in contest loggers: big keys with digits and Latin
letters, "/", "-", "." for the frequency, and ⌫. It types the callsign, frequency and RST, and the button under the
keys moves to the next field: callsign → frequency → RST sent → RST received. Name, city and comment still use the
system keyboard; the built-in one hides meanwhile.

There is a normal and a compact version; the compact one is a third lower and leaves more room for the card.
Choose in Settings → "QSO entry": System, Built-in, Built-in, compact. The choice is remembered and applies to the
normal card and to contest mode.

Gestures: swipe left across the keypad and, without lifting the finger, back right to clear the field with the cursor. Press and hold — the keys fade and
the keypad becomes a trackpad: slide sideways to move the cursor between the letters, as when holding the space bar on
an iPhone; the next key inserts there. The hold time (0.2–1.5 s) is set in "QSO entry" too.

## CAT — transceiver

QSO-LOG talks to the transceiver the way N1MM, Log4OM, WSJT-X or FT8CN do: a few times a second it asks for the
frequency, mode and S-meter. While a new card is open, its frequency, band and mode follow the dial; optionally the
S digit of the RST sent follows the S-meter (59 → 57). A field changed by hand is left alone in that card, saved QSOs
never change. The card and the log show a line "TRX 14 195.000 · USB · S8".

**The transceiver panel** (since 2.2.0) opens with a tap on that line, from the ⋮ menu or with **Ctrl+T**, the same on
the phone and the computer. It has a large frequency you drag left or right like a tuning knob (a finger or the mouse),
turn with the mouse wheel or step with ± by 10 Hz, 100 Hz, 1 or 10 kHz; a field for a frequency ("14195" or "7.074");
band and mode buttons. A band takes the transceiver back to where it last was on it (the first time — to the CW, SSB
or FT8 calling frequency). SSB becomes LSB below 10 MHz and USB above; FT8 and FT4 switch data mode (USB-D) on and
move to the mode's frequency. The band and mode buttons of a new QSO card, and a frequency typed as the callsign
("14025", then Enter), tune the transceiver too — this can be switched off in the CAT settings.

**Scales** (since 2.3.0). Under the frequency the panel has the S-meter on receive, and power, SWR and ALC on transmit
(ICOM and Hamlib rigctld; Kenwood and Yaesu only show that they transmit). Below it is the band scale: the CW,
narrow-band digital, beacon and SSB parts of the IARU plan in colour, the FT8 and FT4 frequencies, and a red needle
where the transceiver is. Tap the scale or drag along it, and the transceiver goes there.

The app never transmits by itself and keeps the CAT port's DTR and RTS lines low. A transceiver cannot tell digital
modes apart, so in DATA (USB-D) the card keeps FT8, FT4 or whatever digital mode it has.

Settings → "Equipment and programs" → **"CAT — transceiver"** (or a long press on the TRX badge): the model, the connection, the port speed and the CI-V address (if the radio does
not answer at the set address, the app tries the factory ones), and what the card takes. The status line shows what
the transceiver answers right now.

| Transceivers | Protocol |
|---|---|
| ICOM: IC-705, IC-7300, IC-7610, IC-9700, IC-905, IC-7100, IC-7851, IC-7800, IC-7700, IC-7600, IC-7410, IC-7200, IC-9100, IC-7000, IC-756PRO, IC-746PRO, IC-910H, IC-718, IC-706MKIIG, IC-703, IC-R8600 and others | CI-V (the address can be set by hand) |
| Xiegu X6100, X6200, G90, X5105, X108G | CI-V compatible |
| Kenwood TS-590, TS-890, TS-990, TS-480, TS-2000; Elecraft K3, K4, KX2, KX3; FlexRadio (SmartSDR CAT) | Kenwood |
| Yaesu FT-991, FT-891, FT-710, FTDX10, FTDX101, FTDX3000, FT-950, FT-450 | Yaesu, new CAT |
| Yaesu FT-817, FT-818, FT-857, FT-897 | Yaesu, old 5-byte CAT |
| any transceiver Hamlib knows | rigctld over the network (also wfview, SDR++, flrig) |
| any transceiver connected to DXLab Commander (Windows) | Commander directives over the network, port 52002 |

Connection:

- **Phone** — the transceiver's USB cable through an OTG adapter (ICOM with a USB socket directly, others with a CI-V/CAT
  cable on a CP210x, FTDI, CH340 or PL2303 adapter; Android asks for USB permission), **Bluetooth** (a transceiver or an
  adapter paired in the Android settings, SPP profile — an IC-705, for one) or the **network** (rigctld, DXLab
  Commander, a network CAT adapter, ser2net).
- **Computer** — a **COM port** (the transceiver's USB cable or a CAT/CI-V interface; some need a CP210x, FTDI or CH340
  driver) or the **network**.

**JTDX, WSJT-X and QSO-LOG with one transceiver** (since 2.4.0). Only one program can open a COM port, so CAT used to
be switched off in the log. Now QSO-LOG shares the transceiver: switch on "Share the transceiver: a Hamlib rigctld
server" in the CAT settings. QSO-LOG keeps the port, and JTDX, WSJT-X, MSHV, GridTracker connect to it as to rigctld:
in JTDX — Settings → Radio → Rig "Hamlib NET rigctl", Network Server `127.0.0.1:4532`, PTT Method "CAT", Split
Operation "None" or "Fake It". The program sets the frequency and mode and keys the radio, while QSO-LOG still shows
the frequency, the panel and the meters. If the program closes while transmitting, QSO-LOG puts the radio back to
receive. With "Let in programs from other computers of the network", JTDX on a computer can use a transceiver
connected to the phone. A separate Hamlib rigctld (Settings → "Equipment and programs" → "Start together with QSO-LOG" → "Add Hamlib rigctld") is only
needed when JTDX must work without QSO-LOG.

**DXLab Commander** (since 2.6.0) is an addition to Hamlib for those whose transceiver is already connected to DXLab
Suite on Windows. Choose "DXLab Commander" in the list of transceivers; the address is the computer with Commander
(`127.0.0.1` on that computer), port 52002 (in Commander: Configuration → Radio → Net Serv, base 52000 + 2). QSO-LOG
takes the frequency, mode and transmit state from Commander, and the panel, the card's buttons and the band scale tune
the radio through it — together with DXKeeper, WinWarbler and the other DXLab programs. There is no S-meter or power
meter through Commander. Hamlib rigctld and the built-in drivers stay as they were.

<p>
<img src="docs/screenshots/73-rig-panel.png" width="200" alt="The transceiver panel on the phone: S-meter and band scale">
<img src="docs/screenshots/78-rig-tx.png" width="200" alt="The panel on transmit: power, SWR, ALC">
<img src="docs/screenshots/69-cat-card.png" width="200" alt="New QSO: frequency and mode from the transceiver">
<img src="docs/screenshots/70-cat-settings.png" width="200" alt="CAT settings on the phone">
<img src="docs/screenshots/71-cat-rigs.png" width="200" alt="Choosing the transceiver">
<img src="docs/screenshots/desktop-24-cat.png" width="440" alt="CAT settings on the computer">
<img src="docs/screenshots/desktop-27-rig-panel.png" width="440" alt="The transceiver panel on the computer">
<img src="docs/screenshots/desktop-29-rig-tx.png" width="440" alt="The panel on transmit on the computer">
</p>

## CW from the F1–F8 keys

As in N1MM, QSO-LOG sends stored CW messages by keying the transmitter with the DTR or RTS line of a USB adapter
(CP2102, CH340, FTDI). Eight messages on **F1–F8** — CQ, exchange, "TU", my and his callsign, repeat, "?", "AGN?" —
take the card's fields: `{MYCALL}`, `{CALL}`, `{RST}`, `{NR}` (serial number sent), `{NAME}`, `{MYLOC}`. The keys work
while the QSO card (the usual or the contest one) is in CW; on a phone without a keyboard the same messages are
buttons under the frequency. **Esc** stops sending, **Alt+↑ / Alt+↓** change the speed (10–50 WPM). The keys can be
reassigned.

Settings → "Equipment and programs" → **"Telegraph (CW) — DTR/RTS or cwdaemon"**: the adapter (through OTG on the phone), the key line, the speed, the
message texts, "The other line is PTT" for radios without break-in, and "Test: send VVV". If CAT uses the same
adapter (one CP2102 cable for both CI-V and the key), choose it here too — the port is shared.

**Xiegu G90** and other radios keyed by shorting to ground: the DTR and RTS pins of a CP2102 go to 0 V while sending.
Wire the pin to the dot (or straight key) contact of the KEY jack through a transistor or an optocoupler (PC817),
ground to ground, and choose the straight key in the radio's menu. If the key is closed when nothing is sent, switch
on "Invert the key line".

**CW over the network — cwdaemon** (since 2.6.0). When the transceiver is wired to another computer (Linux, Raspberry
Pi), [cwdaemon](https://github.com/acerion/cwdaemon) runs there (`sudo apt install cwdaemon`, started e.g. as
`cwdaemon -d ttyUSB0 -p 6789`), and QSO-LOG on a laptop or a phone sends it the F1–F8 messages and the speed over the
network (UDP, port 6789), as tlf and other loggers do. In the CW settings choose "cwdaemon over the network" and enter
the computer's address (`192.168.1.20` or `name:port`). Esc stops sending, Alt+↑ / Alt+↓ change the speed; cwdaemon
reports when a message is over, and the card's "sending" line goes off. cwdaemon takes "+", "-" and "~" as speed and
pause commands, so they are left out of the messages.

<p>
<img src="docs/screenshots/74-cw-card.png" width="200" alt="The card in CW: messages F1–F8">
<img src="docs/screenshots/75-cw-settings.png" width="200" alt="CW settings">
<img src="docs/screenshots/desktop-28-cw.png" width="440" alt="Messages F1–F8 on the computer">
</p>

## Digital modes — WSJT-X, JTDX, MSHV

FT8, FT4 and the other digital modes are worked in WSJT-X, JTDX or MSHV. After each QSO these programs send it over
the network (UDP, port 2237), and QSO-LOG puts it into the log by itself: callsign, start and end time, frequency, mode
(FT8, FT4, JT65, Q65, MSK144…), dB reports, both locators. Name and QTH then come from QRZ.ru, as for any QSO.
Duplicates are not saved: WSJT-X reports a QSO twice (as fields and as an ADIF line), and it goes into the log once.
Plain ADIF over UDP is taken as well.

Settings → "Equipment and programs" → **“Digital modes — WSJT-X, JTDX, MSHV”** → “Take QSOs”. The status line shows whether the program is heard
(“port 2237 · hearing WSJT-X v2.7.0”).

- **Computer.** In WSJT-X: Settings → Reporting → UDP Server — `127.0.0.1`, port `2237`. A QSO is saved when you
  press OK in the “Log QSO” window. JTDX and MSHV have the same fields in their settings.
- **Phone.** WSJT-X on a computer in the same Wi-Fi network sends QSOs to the phone's address, shown in the same
  settings block. QSOs are taken while QSO-LOG is running.
- If another program (JTAlert, GridTracker) already listens on port 2237, set a multicast group in WSJT-X, `224.0.0.1`
  for one, and the same group in QSO-LOG: every program gets the QSOs.
- **Frequency and mode from JTDX** (since 2.2.0). Without CAT in QSO-LOG, while JTDX or WSJT-X controls the
  transceiver, the app takes the frequency and mode from their messages on the same UDP port: the card and the log
  show "JTDX 14 074.000 · FT8", and a new card follows it. Only JTDX can tune the transceiver then; to tune it from
  QSO-LOG too, connect both programs to Hamlib rigctld (see [CAT](#cat--transceiver)).

<p>
<img src="docs/screenshots/72-wsjtx.png" width="200" alt="QSOs from WSJT-X on the phone">
<img src="docs/screenshots/desktop-25-wsjtx.png" width="440" alt="QSOs from WSJT-X on the computer">
</p>

## Contest mode

Contests need speed, so there is a separate mode. Switch it on in the ⋮ menu (below "Reference and calculators") — **CONTEST MODE**
(also in Settings → "Contests", or Ctrl+K on a keyboard).
It turns off every time the app starts, so you don't keep your everyday log in it by accident.

Under the switch you choose what to send:

- **Serial number**: sent as 001, 002… and incremented after every logged QSO.
- **Fixed code**: the same every time: district, region, zone (MO69, EU, 16).

- **Exchange QTH locators** (VHF contests, since 2.4.0): the card gets "My locator" and "Their locator". Yours
  comes from "My station"; working portable, type it in the card and it stays for the next QSOs. The other station's
  locator is filled in when you have worked it before (in an earlier tour or anywhere in the log), and QRZ.ru does not
  overwrite it. The distance to the station shows under the fields. The ЕРМАК / Cabrillo report puts the locators
  after the serial numbers: `… 59 005 LO65FR RA9DEMO 59 017 MO06HU`.

<p>
<img src="docs/screenshots/80-contest-grids.png" width="200" alt="Contest card with locators">
<img src="docs/screenshots/desktop-30-contest-grids.png" width="440" alt="Contest card with locators on the computer">
</p>

In this mode "Add QSO" opens a simplified card: callsign, exchange sent and received, RST. The received exchange can
be anything: 015, MO69, EU, ABCD, 20. RST is filled in (59 or 599) and can be folded into one line. Band and mode are
in the header and change with a tap.

At the bottom is the built-in keyboard. The fastest way:

1. Type the callsign and press "→ Exch".
2. Type the received exchange and press "Log". The QSO is logged with the UTC time of that moment, and the next card
   opens with a new serial.

While you type the callsign, matching calls from the log appear. After logging, "✓ CALL logged" shows briefly, and the
QSO appears in "Latest QSOs" under the card; a tap opens it for correction. The header shows QSOs so far and in the
last hour. A dupe on the same band and mode turns the button red on the first press and is logged only on the second.
Without a received exchange the QSO is not logged: the contest robot would not count it anyway.

Swipe right to left = "Log". Swipe left to right goes back through the contest QSOs to fix one; a card you started is
kept and comes back when you swipe forward. Contest QSOs are flagged in the log, and the exchanges are stored in the
standard ADIF fields `STX_STRING` and `SRX_STRING`, which the Cabrillo and ЕРМАК reports use.

<p>
<img src="docs/screenshots/46-contest-card.png" width="200" alt="Contest card">
<img src="docs/screenshots/53-contest-keypad.png" width="200" alt="Contest card with the built-in keyboard, dark theme">
<img src="docs/screenshots/47-contest-log.png" width="200" alt="Contest QSOs in the log">
</p>

### Contest book

While CONTEST MODE is on, the ⋮ menu has **"Contest book"**. Add each contest you work there: its **name** and
CONTEST **code** (goes into the ЕРМАК/Cabrillo header), **start and end** in UTC, and **tours** for minitests — tour
length in minutes and how many (e.g. 12 tours of 10 minutes; the end then follows from the last tour). With **"Reset
dupes with each new tour"** the same station can be worked again in every tour.

Every QSO from the contest card gets the chosen contest, its code (ADIF `CONTEST_ID`) and the tour number. The log
header and the card show the contest, the current tour and the time left; the QSO count and rate are for that contest
only. Dupes count over the whole contest, or within the current tour when they reset. The ЕРМАК/Cabrillo export with
"Contest-mode QSOs only" lets you pick the contest: only its QSOs go into the file, with its CONTEST code.

<p>
<img src="docs/screenshots/64-contests.png" width="200" alt="Contest book">
<img src="docs/screenshots/65-contest-edit.png" width="200" alt="New contest: minitest tours">
<img src="docs/screenshots/66-contest-tour.png" width="200" alt="Tour and timer in the log header">
</p>

## Keyboard only

A whole outing or contest can be done without touching the screen: on the computer with its keyboard, on a phone or
tablet with an external one (Bluetooth or USB OTG). Handy in the field: phone on a stand, small keyboard on your lap.
With an external keyboard attached, the built-in on-screen keyboard hides and the cursor is in the callsign.

All the keys below are the defaults: any of them can be replaced in Settings → "Interface" → "Assign keys"
(since 2.2.0), and the key list (F1) then shows yours.

It works as in classic loggers (on the phone since 0.40.0, on the computer since 2.0.0): **Space** goes round the fields: callsign → RST sent →
RST received → callsign again (in a contest RST is skipped: callsign ↔ received exchange). **Enter** in any field,
the callsign included, logs the QSO and opens a new card. **Alt+W** or **Ctrl+Esc** wipes the card when the station
fades into QSB: callsign, data and reports are cleared, the cursor is in the callsign. **Esc** closes the card (if
something is typed it asks; Enter closes). **Ctrl+D** on a saved QSO or a log record deletes it, Enter confirms. With
a keyboard attached, the "Add QSO" button shows "Ctrl+N · F9". **Delete** on a log record deletes it too. Made a
typo? **↑** in an empty callsign opens the QSO you just logged; Enter saves it and brings you back to a new card.

Keys by screen. On macOS use ⌘ instead of Ctrl.

**Everywhere**

| Key | Action |
|---|---|
| Ctrl+N or F9 | new QSO |
| Ctrl+E | fix the QSO just logged |
| Ctrl+F | search the log; Enter in the search — new QSO with the station found, ↓ — to the list |
| Ctrl+H | search history |
| Ctrl+D · Ctrl+M · Ctrl+, | dashboard (Ctrl+D on a selected record deletes it) · QSO map · settings |
| Ctrl+K | CONTEST MODE on or off |
| Ctrl+R | sync with the Google Sheet |
| Ctrl+Shift+E | export the log to ADIF |
| F1 (on Android also Ctrl+/) | list of all keys |

**Log**

| Key | Action |
|---|---|
| ↑ ↓ | through the rows (the current one is outlined) |
| PgUp · PgDn · Home · End | 10 rows, first, last |
| Enter | open the row |
| Space · Ctrl+A | select the row · select all (for export) |
| Delete · Ctrl+D | delete the row or the selected ones: a dialog asks, Enter deletes (undo is offered after) |
| Esc | clear the selection |

**QSO card**

| Key | Action |
|---|---|
| Space | round the fields: callsign → RST sent → RST received → callsign (a contest QSO: callsign ↔ received exchange) |
| Enter | from any field — log; in a new card — log and open the next one |
| Tab · Shift+Tab | next and previous field |
| ↑ | in an empty callsign — fix the last QSO |
| Alt+W · Ctrl+Esc | wipe a new card (band, mode and frequency stay), the cursor goes to the callsign |
| Esc | close the card (with changes it asks, Enter closes) |
| Ctrl+D | delete the saved QSO (Enter confirms) |
| F2 · F3 · F4 · F5 | to callsign, frequency, RST sent, RST received |
| F6 | current UTC time |
| F7 | voice note: start or stop |
| F8 (on a Mac also ⇧⌘S) | save and open the next |
| F12 (on a Mac also ⌘S) | save |
| Alt+1…9 · Alt+Shift+1…9 | band · mode (the n-th switched on) |
| Shift+Enter | new line in the comment |

**Contest mode**

| Key | Action |
|---|---|
| Space | callsign ↔ received exchange (RST is skipped) |
| Enter · F12 | log the QSO from any field |
| ↑ · PgUp | previous QSO (↑ in an empty callsign) |
| PgDn | next QSO |
| Alt+W · Ctrl+Esc | wipe the card |
| Esc | close the card |

**Search history**

| Key | Action |
|---|---|
| ↑ ↓ · Enter | through the rows · open with the map |
| Space · Ctrl+A | select the row · select all |
| F | add to or remove from favourites |
| E | edit (the selected ones at once) |
| N | new QSO with this station |
| M | all on the map or as a list |
| Delete | delete from the history |
| Esc | back |

**Dashboard**: 1 · 2 · 3 · 4 — 7 days, 30 days, year, all time; Esc — back.

On small keyboards without F keys, commands typed in the callsign field help. Type a command instead of a callsign and
press Enter or Space; the app switches band, mode or frequency and clears the field:

| Type | Result |
|---|---|
| `20m` or `20`, `70cm` or `70` | band |
| `CW`, `SSB`, `FT8`, `FM`… (`USB` and `LSB` mean SSB) | mode |
| `14195` or `14.195`, `7074` | frequency in kHz or MHz, the band follows |

A command can't be mistaken for a callsign: a callsign always has both letters and digits.

The whole list is the first reference tab, **"Keys"**, and the **"Key list"** button in Settings → "QSO entry"; on a
phone both open without a keyboard attached. The ⋮ menu has a "Keys" item (on a phone when a keyboard is attached),
and the shortcuts are shown in the menu items. On a Mac press the F keys with fn unless "Use F1, F2, etc. keys as standard function keys" is
on in the keyboard settings.

<p>
<img src="docs/screenshots/56-keyboard-keys.png" width="200" alt="Key list on the phone">
<img src="docs/screenshots/desktop-18-keys.png" width="440" alt="Key list on the computer">
</p>

## Log

QSOs are grouped by day, newest first. A row shows callsign, time, frequency, mode, RST, distance, name and city.
A microphone icon means the QSO has a voice note.

- **Search** looks in callsign, name, city, locator and comment. If the callsign is not in your log, the app looks it up
  on QRZ.ru and opens a new card with what it found.
- If the search narrows to one station you know, the bands you worked it on are shown under the search, with the last
  QSO on each. "Add QSO" then opens a card with its name and city.
- **Sorting**: by date, distance, callsign or band. A second tap on the same button reverses the order.
- A QSO saved without internet has a ⟳ button. Tap it when you are online and the QRZ.ru data is filled in.
- Select several QSOs with a long press (on the computer also Ctrl-click or ⌘-click) and export them with "Export".
  The filter button next to it selects the QSOs of a period, bands and modes (since 2.5.0).
- A QSO is deleted only from its card. The app asks first, and after deleting you can still tap "Undo".
- The ⋮ button in the top right opens the menu: settings, dashboard, QSO map, search history, reference.

## Dashboard

The log in charts: ⋮ menu → "Dashboard". Everything is counted on the device, without internet.

One row of filters at the top applies to all charts: period (7 days, 30 days, year, all time), band and mode
(all or one), and "Contest" — only contest-mode QSOs.

| Block | What it shows | Forms |
|---|---|---|
| Summary | QSOs, callsigns, DXCC entities and continents, active days and per-day average, best day, longest QSO | numbers |
| QSOs over time | per day, week or month (picked for you or by hand), empty days shown | columns, line, running total, columns split by band |
| Bands | QSOs per band, in frequency order | bars or ring |
| Modes | SSB, CW, FT8… | bars or ring |
| When you are on the air | heat map: weekday × UTC hour | — |
| Most worked | the 10 callsigns with the most QSOs | bars |
| Countries | every DXCC entity in the log: QSOs, callsigns, continent, bands, last QSO; by QSOs, A–Z or recent | list with bars |
| Continents | by callsign prefix, from cty.dat | bars |
| Distance | QSOs up to 100 km, 100–500 km and so on to 10,000 km and beyond | bars |

A tap on a column, point or cell shows the exact number. The charts use the app's colours: QSO-LOG blue first, then
amber, green, violet and rose. A band keeps its colour whatever the filter. The palette was checked for colour-vision
deficiencies in the light and dark themes.

<p>
<img src="docs/screenshots/50-dashboard.png" width="200" alt="Dashboard: summary and QSOs over time">
<img src="docs/screenshots/52-dashboard-share.png" width="200" alt="Mode shares and heat map">
<img src="docs/screenshots/60-dashboard-countries.png" width="200" alt="Countries">
</p>

## Search history

⋮ menu → "Search history". Every callsign you look up — in the log search or in the "New QSO" card — lands here by
itself. It is a lookup cache: no QSOs come from it, and it is not counted. If you already have QSOs with the station,
the row says "in the log: N QSO". Each entry shows where the data came from (QRZ.ru, the QRZ.ru site, QRZ.com or
HamQTH) and when.

Switch the history on on its screen (⋮ menu → "Search history"); the app explains first what it is for. The main use is working
without internet: when QRZ.ru or QRZ.com don't answer, the new QSO card is filled from the history (marked "From the
search history"). The entries stay on the device.

What you can do:

- open an entry: map with the path from you to the station, distance, bearing and back bearing, name, QTH, locator,
  RDA, zones; "New QSO" creates a QSO with this data;
- star it — favourites are always at the top;
- sort (newest, by callsign, nearest, by country) and filter: favourites only, by source, only with a position, text search;
- see all entries on one map, favourites in amber; a tap on a label opens the entry;
- edit one entry or several at once (long press or the select button): filled fields change in all selected entries,
  empty ones stay as they are; delete one or several.

<p>
<img src="docs/screenshots/57-history.png" width="200" alt="Search history">
<img src="docs/screenshots/58-history-map.png" width="200" alt="Search history on the map">
<img src="docs/screenshots/59-history-entry.png" width="200" alt="History entry with map and distance">
</p>

## Station data

The app looks for the other station's name, city and position in four places, in this order. All of them are set up
in Settings → "Callbooks and online logs" → "Station data sources".

1. **QRZ.ru XML API.** The main way, meant by QRZ.ru for logging programs. QRZ.ru issues a separate API login:
   on qrz.ru open your personal data, section "XML API", "Create account"
   ([direct link](https://www.qrz.ru/personal/settings/apiuser)); give your callsign and the program name, QSO-LOG.
   The API gives no RDA district, so for a Russian callsign the app also reads it from the public callsign page.
2. **QRZ.ru site account.** A fallback without API access: your qrz.ru e-mail and password; the app reads the
   callsign page. It breaks if the site changes its layout.
3. **QRZ.com site.** For callsigns QRZ.ru doesn't know. Your QRZ.com login (callsign or e-mail) and password; a free
   account is enough. Gives name, address, country, locator, position and CQ/ITU zones. Two-factor login is not supported.
4. **HamQTH.** No account needed, but only country, region and CQ/ITU zones. The distance is then to the centre of the
   region and marked "≈". Can be switched off.

Without internet the QSO is saved anyway; fill in the data later with ⟳.

## Voice notes

Sometimes it is easier to say than to type. Recording starts in three ways: hold "Add QSO" (release — the card opens
with the note); tap the microphone on that button and tap again to stop; or tap the microphone in the header of a new
card (F7 on a keyboard). Play the note with ▶ next to the callsign; share or delete it from its ⋮ menu.

## Distance and maps

Distance and bearing are counted from your QTH locator. The card shows the great-circle path; "Map" opens it full
screen. On an expedition you can change the locator in the QSO itself. "QSO map" in the ⋮ menu shows all stations you
worked on OpenStreetMap; a tap on a point opens the last QSO with that station.

<p>
<img src="docs/screenshots/03-card-map.png" width="200" alt="Distance, bearing and path">
<img src="docs/screenshots/04-map.png" width="200" alt="QSO map">
</p>

## Export and import

All formats are in one window: ⋮ menu → **"Export and import…"** (on the computer, the "File" menu; Settings → "Logbook" has a button for the same window).

- **ADIF** (.adi) for LogHX, UR5EQF, HamLog, N1MM, QRZ.com and others, and for moving the log between phone and
  computer. Choose UTF-8 or Windows-1251 when exporting; on import the encoding is detected and all fields are kept.
- **CSV** for Excel, separated by semicolons.
- **Cabrillo 3.0** for international contests and **ЕРМАК** ([ermak.srr.ru](https://ermak.srr.ru/)) for Russian
  ones. The app asks for the contest code and category first.

To export part of the log (since 2.5.0), press **"Part of the log…"** in the "Export and import" window (on the computer,
"File" → "Select part of the log…"): a period (today, 7 days, 30 days, this year or your own UTC dates), bands and
modes. The matching QSOs become selected in the log — tap to add or remove — and "Export" saves just them. QSOs can
also be picked by hand with a long press. Selected QSOs are all exported: "All selected" is the default, and the
ЕРМАК / Cabrillo dialog does not switch on "Only contest-mode QSOs" for them.

<p>
<img src="docs/screenshots/82-select-dialog.png" width="200" alt="Selecting part of the log: period, bands, modes">
</p>

Before saving you choose whether to take only QSOs not yet exported in this format (the default for the whole log),
and whether to mark them as exported. The marks are shown
at the bottom of the card, and coloured dots next to the time in the log: blue ADIF, green CSV, orange contest report.
Every QSO has a permanent GUID (ADIF `APP_QSOLOG_UID`), so duplicates are skipped on import.

## Online logbooks: LoTW, eQSL, Club Log, QRZ.com

Since 2.3.0 QSOs go to LoTW (ARRL), Club Log and the QRZ.com logbook, since 2.6.0 to eQSL too: ⋮ → "Export and import" → "Online logbooks",
or "Export" on picked records. The dialog asks which logbook, only new QSOs (the default) or all, and whether to mark
them. The mark with date and time shows in the QSO card; take it off and the QSO goes again.

- **QRZ.com Logbook** needs the logbook's API key: qrz.com → My Logbook → Settings → "API Access Key" (logbook API
  access comes with a QRZ.com subscription). Duplicates count as "already there".
- **Club Log** needs the account's e-mail and password and an application key, which Club Log gives on request
  (Help Desk → "Request an API key"). The callsign comes from "My station".
- **eQSL** needs the eQSL login (callsign) and password, and the QTH Nickname if the callsign has several profiles.
  Duplicates count as "already there".
- **LoTW**: ARRL's TQSL signs the QSOs with your certificate. On the computer QSO-LOG runs TQSL itself: enter the
  Station Location (the name of the station location in TQSL); the app finds TQSL by itself. There is no TQSL for
  the phone: QSO-LOG saves an ADIF file you sign in TQSL on a computer ("Sign a log and upload it"), and the QSOs
  are marked "saved for TQSL".

**eQSL cards** (since 2.6.0): ⋮ → "Export and import" → **"eQSL cards"** downloads the eQSL inbox — the cards sent to
you. When the log has a QSO with the same station (a callsign with /P counts as the same), on the same band and mode
and no more than 30 minutes apart, it gets the "eQSL received" mark with the card's date (shown in the QSO card, in the
"Export" block, and exported to ADIF as `EQSL_QSL_RCVD`). Cards with no QSO in the log are listed: tick the ones you
want and press "Add to the log" — they become new QSOs (the received RST taken from the card). Downloading again
duplicates nothing.

The accounts are in Settings → "Callbooks and online logs" → **"Online logbooks: LoTW, eQSL, Club Log, QRZ.com"**. The password and keys are stored
encrypted (Android's keystore on the phone, the system keychain on the computer).

<p>
<img src="docs/screenshots/79-upload.png" width="200" alt="Upload to an online logbook">
</p>

## Sync through a Google Sheet

One log on two or three devices. QSOs are collected in your own Google Sheet, and the app exchanges them when you press
"Sync" — nothing is sent in the background. The app writes through a small Apps Script in the sheet, so no Google
sign-in is needed in the app:

1. Open a Google Sheet (a new empty one is fine) → Extensions → Apps Script; replace the code with the script from the
   app's settings or [qsolog-sheet-sync.gs](shared/src/main/resources/qsolog-sheet-sync.gs).
2. Deploy → New deployment → Web app, "Execute as: me", "Who has access: anyone"; allow access.
3. Copy the web app URL (ending in `/exec`) into "Script address" on every device and press "Sync".

The first sync merges the logs; the same QSO from two devices becomes one row. Later edits win, rows edited in the sheet
come back to the devices, and a deletion on one device deletes on the others. The script address works like a password.

## Reference and calculators

⋮ menu → "Reference and calculators", offline: Russian band plan, IARU Region 1 plan and activity frequencies, Morse (tap a sign to hear it; Russian letters show the Latin letter with the same signal, W/В, and the Russian chant used to learn them; digits and signs have chants too),
phonetic alphabets, Q-codes, power levels and EIRP, cable loss, antenna sizes, prefixes (country, zones, distance; RDA
for Russia), propagation indices and Russian time zones.

### Morse trainer

The Morse tab has modes at the top: Table, Text (on the phone), Sending and Receiving.

- **Text.** Paste or type any text — Latin, Cyrillic or mixed — and press Play: it sounds in Morse at 5–40 words per
  minute (the speed can change on the fly). The sounding character is highlighted, the text scrolls along, and a tape
  below shows each character with its code. Tap a character on the tape to play from it.

- **Sending.** The app shows a group of characters, you key it, and what the app "heard" appears below: right
  characters in green, mistakes in red. The key is on the screen (one pad for a straight key, two — "·" and "−" — for
  paddles) or a real one. For paddles the app works as an electronic keyer, iambic mode A or B; for a straight key it
  follows your timing and shows your speed. A 700 Hz sidetone sounds.
- **Receiving** by the Koch method. The app plays a group of five, you type what you heard (any keyboard layout: K counts
  as К). Two characters first; a new one is added when the last five groups are 90% right. Mistakes are shown with their
  signal and chant. Characters sound at full speed, the gaps between them can be longer (Farnsworth).
- Speed, key type, alphabet (Cyrillic or Latin) and the number of learned characters are remembered.

To connect a real key or paddle (for example a Xiegu with a 3.5 mm plug). The Sending mode has a "Connect a key" button
with the same instructions, the adapters the phone sees, a live check (press the key and see what arrives and how it is
understood), and assignment: press "Assign" and then the key, so an adapter sending any keys works.

- **A USB or Bluetooth adapter that sends key presses like a keyboard** (ready-made, such as the Vail Adapter, or home-made
  on a Seeed XIAO, Arduino Pro Micro or ESP32): dot — left Ctrl or "[", dash — right Ctrl or "]", a straight key — any of
  them or Space. Phone via USB OTG, computer via any USB port.
- **On a phone, as a headset button** through a 3.5 mm jack or a USB-C → 3.5 mm adapter with a microphone. In the Android
  headset standard, shorting the microphone contact to ground is the headset button (straight key), through 240 Ω
  volume + (dot), through 470 Ω volume − (dash). Latency is higher, and not every phone passes these buttons to the app.

<p>
<img src="docs/screenshots/61-morse-send.png" width="200" alt="Morse trainer: sending">
<img src="docs/screenshots/62-morse-receive.png" width="200" alt="Morse trainer: receiving by the Koch method">
<img src="docs/screenshots/63-morse-connect.png" width="200" alt="Connecting a key: ways, check, assigning keys">
</p>

## Settings

Since 2.5.0 the settings are two levels deep; each block header says briefly what is chosen. The main page: My station;
QSO entry (time, keyboard and its hold time); Contests (CONTEST MODE, what is sent, locator exchange); bands and modes
shown in the card; **Equipment and programs ›** — a page with CAT, digital modes, CW and "Start together with QSO-LOG"
(on the phone, apps such as FT8CN or an SDR app that open with QSO-LOG and keep running in the background while QSO-LOG
comes back in front — or stay in front, with "Come back to QSO-LOG at once" off; Xiaomi, Redmi and Poco need "Display
pop-up windows while running in the background" for QSO-LOG, and since 2.6.0 the page says so and opens that screen; on the computer, any programs with their arguments, such as JTDX, WSJT-X or Hamlib rigctld, not
opened twice and optionally closed with QSO-LOG; the page also opens with a long press on the TRX badge on the phone,
and from "File" → "Equipment and programs…" on the computer); **Callbooks and online logs ›** — a page with the station
data sources and the LoTW, eQSL, Club Log and QRZ.com accounts; logbook ("Export and import…", "Select part of the log…",
sync — its header says whether Google sync is on, delete all); interface (language: system, Russian, English — changes
at once; theme; keep the screen on (phone); "Assign keys" — any key of an external keyboard can be replaced, added or
taken off). At the bottom: version, "What's new", "Report a problem" and
"Check for updates". "Report a problem" opens a new GitHub issue in the browser with a template, the app version and
the device model — no log, callsign or passwords; you send it yourself (a GitHub account is needed).

<p>
<img src="docs/screenshots/en-03-language.png" width="200" alt="Language">
<img src="docs/screenshots/11-dark.png" width="200" alt="Dark theme">
<img src="docs/screenshots/81-settings-hardware.png" width="200" alt="Equipment and programs">
<img src="docs/screenshots/77-keys-editor.png" width="200" alt="Assigning keys">
</p>

All `*DEMO` callsigns in the screenshots are made up.

## On the computer

The desktop version does everything the Android one does, CAT included (a COM port or Hamlib rigctld). Since 2.0.0
the phone and the computer share version numbers. The window has two parts: the log on the left, the card,
map, dashboard, history or settings on the right. Every key is in [Keyboard only](#keyboard-only); F1 shows them in the app.

<p>
<img src="docs/screenshots/desktop-en-01-log.png" width="440" alt="Log on the computer">
<img src="docs/screenshots/desktop-en-02-new-qso.png" width="440" alt="New QSO on the computer">
</p>

Differences from the phone: voice notes are WAV (M4A on the phone); "Share" copies the QSO to the clipboard; export,
import and the reference are also in the File menu.

## Installation

Install a new version over the old one; the log and settings are kept.

### Android

Download `QSO-LOG-0.40.2.apk` from the [release page](../../releases/tag/v0.40.2) and open it on the phone; allow
installing from this source if Android asks.

### macOS

Download `QSO-LOG-2.6.0-macos-arm64.dmg` (Apple silicon) or `-macos-x64.dmg` (Intel) from the
[release page](../../releases/tag/desktop-v2.6.0), open it and drag QSO-LOG to Applications. The app is not signed by
Apple: on the first start go to System Settings → Privacy & Security and click "Open Anyway".

### Windows

Download `QSO-LOG-2.6.0-windows-x64.zip` from the [release page](../../releases/tag/desktop-v2.6.0), unpack the
whole archive and run `QSO-LOG.exe` (Java is included). If SmartScreen warns, click "More info" → "Run anyway".

### Linux

Download `QSO-LOG-2.6.0-linux-amd64.deb` from the [release page](../../releases/tag/desktop-v2.6.0) and install it:

```bash
sudo apt install ./QSO-LOG-2.6.0-linux-amd64.deb
```

## Where the data is kept

Only on your device:

| System | Log folder | QRZ passwords |
|---|---|---|
| Android | inside the app | Android encrypted storage |
| macOS | `~/Library/Application Support/QSO Log` | Keychain |
| Windows | `%APPDATA%\QSO Log` | Credential Manager |
| Linux | `~/.local/share/qso-log` | GNOME Keyring or KWallet, else the settings file |

The app goes online only for station data (api.qrz.ru, www.qrz.ru, www.qrz.com, hamqth.com), OpenStreetMap tiles,
GitHub when checking for updates, and your Google Sheet (script.google.com) when you press "Sync".

## Known issues

- The Android app is installed by hand; it is not on Google Play.
- The macOS and Windows builds are not signed, so the system warns on the first start.
- The Windows build is made on a Mac and has not been tested on real Windows yet. Please report problems in
  [issues](../../issues) — in English or Russian.
- On Linux "system" theme is always light; switch to dark by hand.
- On the phone, QSOs from WSJT-X are taken only while QSO-LOG is running: Android may stop an app left in the
  background for long. Missed QSOs move over with the ADIF file from WSJT-X.
- Tuning the transceiver and CW have been tested on protocol simulators, not on live radios of every model. Old Yaesu
  radios with their own 5-byte CAT (FT-840, FT-890, FT-990, FT-1000) are not supported yet; they can be reached through
  Hamlib rigctld on a computer.
- DXLab Commander has been tested on a simulator of its network protocol, cwdaemon on a real cwdaemon 0.10 without a
  radio. The eQSL download has been tested on eQSL's test account.

## For developers

Kotlin. The shared part (log, formats, calculations, QRZ.ru, QRZ.com, HamQTH, sheet sync, reference, the CAT protocols
in `shared/…/cat/`, WSJT-X reception in `shared/…/digi/`) is the same for all systems; the transceiver's port is usb-serial-for-android and Bluetooth SPP on
Android, jSerialComm on the computer; Android uses Jetpack Compose, the desktop Compose Multiplatform. `shared/` — common code, `app/` — Android,
`desktop/` — macOS, Windows, Linux. Needs JDK 17 (Temurin) and Android SDK 34.

```bash
./gradlew :app:assembleRelease         # Android APK (R8, signed with the debug key)
./gradlew :shared:test :desktop:test   # tests
./gradlew :desktop:run                 # run the desktop version
./gradlew :desktop:packageReleaseDmg   # DMG for your Mac
./desktop/windows/build-windows.sh     # Windows ZIP (built on a Mac)
./desktop/linux/build-linux.sh         # Linux DEB (built on a Mac with Docker)
```

## License

[MIT](LICENSE). Licenses of fonts, libraries and reference data are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
