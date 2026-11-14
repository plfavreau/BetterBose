# BMAP on the QC Ultra Headphones (2nd gen), verified 2026-11-14

Device: product id `0x4082` variant `01`, firmware `10.12.14+gde0210e`, BMAP `1.2.0`.
Transport: RFCOMM, secure socket on SPP UUID `00001101-0000-1000-8000-00805f9b34fb`.
Cold connect ~4 s, warm reconnect ~250-300 ms. Coexists with the official Bose app installed.

Frame: `[block][function][op][len][payload]`. Ops: 0 SET, 1 GET, 2 SETGET, 3 STATUS,
4 ERROR, 5 START, 6 RESULT, 7 PROCESSING. Match replies by (block, function).

| What | Addr | Observed reply | Notes |
|---|---|---|---|
| Fblock bitmask | 0.2 | `87 cc 23 ff` | |
| Product name | 1.2 | `00` + UTF-8 | |
| Battery | 2.2 | `0a ff ff 00` | byte 0 = percent |
| CNC level | 1.5 | `0b 00 03` | 11 steps, current 0 |
| EQ | 1.7 | `f6 0a 00 00 / f6 0a 00 01 / f6 0a 00 02` | [min,max,value,band] x3, all flat |
| Multipoint | 1.10 | `07` | bit0 on, bit1 supported |
| Wear detect | 1.24 | `01` | |
| Shortcut button | 1.9 | `80 09 01 00 09 40 02 ...` | |
| Volume | 5.5 | `1f 09` | [max=31][current] |
| Spatial (read) | 5.15 | ERROR 4 | use 31.10 / mode records instead |
| Live settings | 31.10 | `00 00 00 00 01` | [cnc inverted][autoCnc][spatial][wind][anc] |
| Current mode | 31.3 | `00` | |
| Mode record | 31.6 + `[idx]` | 48 bytes | name at 6..37, slots 0..10 |
| **Set mode** | 31.3 START `[idx, announce]` | RESULT `[idx]` | verified 0 -> 1 -> 0 |

Modes on this unit: 0 Quiet, 1 Aware, 2 Immersion, 3 Cinema, 4 Focus (user), 5-10 empty ("None").

References: github.com/aaronsb/bosectl, github.com/Alexma03/open-headset-control,
github.com/kaledcorona/bose-rfcomm.
