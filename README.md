# PotteryRunes

Plugin na Paper 1.21.x: **Rune** oparte na pottery sherdach. Każda runa ma własną zdolność, a gracz może mieć tylko **jeden typ runy naraz**.

## Użycie
- `/rune give <typ> [gracz] [ilość]` - nadaje runę (permisja `runes.admin`)
- `/rune list`, `/rune reload`
- **PPM** z runą w ręce = aktywna zdolność. Pasywki działają, gdy runa jest w ekwipunku.
- Drugi typ runy jest blokowany przy podnoszeniu, a jeśli jakoś trafi do ekwipunku - wypada na ziemię.

## Runy
| Runa | Sherd | Cooldown | Zdolność |
|---|---|---|---|
| Breeze | Breeze | 40s | Dash 20 bloków, obrażenia rosną z dystansem |
| Flame | Burn | 50s | Pasyw: Fire Res (Nether: Resistance 1). Aktywnie: woda wysycha r=10 przez 10s |
| Explosion | Danger | 30s* | Fireball 4 serca |
| Wind | Flow | 50s | ~40 bloków w górę i slam z fall damage dla otoczenia |
| Frost | Snort* | 120s | Zamraża losowego gracza r=15 na 3s |
| Blade | Blade | 80s | Strength 3 na 15s, potem czyści efekty |
| Miner | Miner | 60s | Pasyw Haste 1, aktywnie Haste 3 na 30s |
| Heart | Heart | 67s | +2 serca, Resistance 3 na 10s |
| HeartBreak | Heartbreak | 120s | +2 serca, ostatnio trafiony traci 2 serca na 30s |
| Ancient | Mourner | 360s | Sculkowa klatka-kula r=16 |

\* wartości nieustalone w konspekcie - zmień w `config.yml`.

## Build
```
mvn package
```
Plik: `target/PotteryRunes.jar` → do `plugins/`. (GitHub Actions buduje jar automatycznie.)

## Wrzucenie na GitHub
```
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<twoj-nick>/PotteryRunes.git
git push -u origin main
```
