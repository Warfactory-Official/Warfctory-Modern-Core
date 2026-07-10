# Computation Mainframe — Thermal Model & Balancing Guide

This document describes exactly how the Computation Mainframe generates and sheds heat, and gives
Desmos‑ready formulas so the numbers can be balanced by hand. Everything here is derived from the
source; the "Where it lives" column tells you which constant to edit to change a number.

## 1. The pieces

| Quantity | Meaning | Where it lives |
|---|---|---|
| CPU `efficiency` | 0..1; lower ⇒ more waste heat | `CPURegistry.register()` → `new CPUEntry(0.5, …)` |
| CPU `maxPower` | EU/t the CPU runs at (always max) | `CPUEntry` arg = `GTValues.V[HV]` = **512** |
| `EU_TO_HEAT_RATIO` | waste EU → heat units | `CPURegistry.CPUEntry` = **0.04** |
| `PASSIVE_BASE_COEFF` | passive cooling per fan tier | `CoolingPartMachine` = **0.05** |
| `ACTIVE_COOL_SCALE` | liquid cooler strength | `CoolingPartMachine` = **0.1** |
| fluid draw per liquid cooler | mB/t consumed | `CoolingPartMachine.getFluidUsagePerTick()` = **100** |
| coolant `heatCapacity` | heat carried per mB | `CoolantRegistry.register()` — water 1, oxygen 3, helium 6, nitrogen 10 |
| `MAX_TEMP` | explode threshold | `MainframeMachine` = **105 °C** |
| throttle threshold | sag begins | `GPCHandler.calculateSag()` = **90 °C** |
| active force‑cool threshold | liquid dumps at full | `tickMainframe()` = **70 °C** |
| thermal mass | `500 + 50 × (#CPU + #RAM + #coolers)` | `GPCHandler.rebuild()` |
| idle cooldown | when not computing | `tickMainframe()` = **0.25 °C/tick** toward ambient |

**Heat units:** everything below is in the game's internal "heat units per tick." Temperature change
per tick = (net heat units) ÷ (thermal mass). Thermal mass **cancels at equilibrium** — it only sets how
fast the temperature moves — so for balancing steady‑state temps you can ignore it.

## 2. How it actually works (plain English)

- Each CPU always runs at **max power**, producing a fixed amount of compute (CWU) and a fixed amount of
  waste **heat**. The mainframe's heat output scales with how much of that compute is actually being
  **used** (`utilization`), not with CPU power.
- **Passive coolers (fans)** bleed heat to the air. Cooling grows the hotter the mainframe runs above
  ambient (Newton's law of cooling). A **Cooling Fan Cover** raises a fan's effective tier LV..EV, making
  it cool `(tier+1)×` a bare fan.
- **Liquid coolers** pump coolant (100 mB/t each). Colder coolants carry more heat. Below 70 °C a liquid
  cooler removes only what's needed (a thermostat); at/above 70 °C it dumps at full power.
- **Thermal sag**: above 90 °C the mainframe throttles its own compute, which cuts heat. This makes the
  machine **self‑limit (throttle) rather than explode** in most cases — so cooling mostly buys you
  **sustained throughput**, not survival.

## 3. Desmos model

Paste these in. Undefined names become sliders. Current in‑code values are shown.

### Constants (edit to match code)
```
P_m = 512          # CPU max power  (GTValues.V[HV])
b_e = 0.5          # CPU base efficiency
r   = 0.04         # EU -> heat ratio
k   = 0.05         # PASSIVE_BASE_COEFF
s   = 0.1          # ACTIVE_COOL_SCALE
Q   = 100          # mB/t per liquid cooler
T_a = 22           # ambient (biome-based; see caveats)
```

### Your build (the sliders you tune)
```
n      = 4         # CPUs
n_0    = 0         # bare fans
n_1    = 0         # LV-cover fans
n_2    = 0         # MV-cover fans
n_3    = 0         # HV-cover fans
n_4    = 4         # EV-cover fans
c_1    = 0         # water coolers    (heat cap 1)
c_3    = 0         # oxygen coolers   (heat cap 3)
c_6    = 0         # helium coolers   (heat cap 6)
c_{10} = 0         # nitrogen coolers (heat cap 10)
```

### Derived
```
E_f = max(0.05, b_e - 0.2)                          # CPU efficiency at full load
C_1 = P_m * E_f                                      # CWU per CPU
H_1 = P_m * (1 - E_f) * r                            # heat per CPU (units/tick)
D   = n * H_1                                         # total heat @ 100% utilization
W_x = n * C_1                                         # total CWU
K   = k * (n_0 + 2 n_1 + 3 n_2 + 4 n_3 + 5 n_4)      # total passive coefficient
A   = s * Q * (c_1 + 3 c_3 + 6 c_6 + 10 c_{10})      # total active cooling (units/tick)
```

### The curves (x-axis = temperature T)
```
S(T)   = min(1, {T>90: 0.5*((T-90)/10)^2, 0})        # thermal sag (throttle)
C(T)   = K * max(0, T - T_a)                          # passive cooling at temp T
Y_1(T) = D * (1 - S(T))                               # heat produced once throttled
Y_2(T) = C(T) + A                                     # total cooling available
```
Graph `Y_1` and `Y_2`. **Their intersection is the steady‑state temperature `T*`.** Add vertical lines
at `T=90` (throttle) and `T=105` (explode).

### Readouts
```
# Simple steady temp (valid when it lands below 90, i.e. no throttling):
T_star = T_a + max(0, D - A) / K

# Usable compute at the real steady state T* (from the Y_1∩Y_2 crossing):
u_star = 1 - S(T*)
CWU_sustained = W_x * u_star

# Response speed / safety (uncooled worst case):
M = 500 + 50*(n + n_0+n_1+n_2+n_3+n_4 + c_1+c_3+c_6+c_{10})   # thermal mass
t_boom = (105 - T_a) * M / D        # ticks from ambient to boom, uncooled  (÷20 = seconds)
```

## 4. How to balance

1. Decide a **target steady temperature** (well under 90 °C to avoid throttling, or accept some throttle
   for a cheaper build).
2. Slide fan/cooler counts until the `Y_1 ∩ Y_2` crossing sits at that temperature.
3. Check `u_star` — that's the fraction of your CWU you actually get. Below 90 °C it's 100%.
4. **Key lever:** better cooling → lower `T*` → less sag → higher `u_star` → more usable compute. Cooling
   buys throughput, not just survival.

Per‑part cheat sheet (current constants, per CPU = 14 heat units, ambient 22 °C):

| Cooler | Cooling contribution |
|---|---|
| bare fan | `0.05 × (T − 22)` units/tick |
| LV / MV / HV / EV covered fan | `0.10 / 0.15 / 0.20 / 0.25 × (T − 22)` |
| water / oxygen / helium / nitrogen cooler | `10 / 30 / 60 / 100` units/tick (flat, when coolant is supplied) |

## 5. Worked example (current constants)

Per CPU: `E_f = 0.3`, `C_1 = 154 CWU`, `H_1 = 512·0.7·0.04 = 14.3 heat`. For `n = 4` CPUs → `D ≈ 57`.

- **4 EV fans**, no liquid: `K = 0.05·(5·4) = 1.0` → `T* = 22 + 57/1.0 = 79 °C`, below throttle →
  **100% compute**.
- **2 EV fans** only: `K = 0.5` → un‑throttled `T*` would be 136 °C, so it throttles instead; the
  `Y_1∩Y_2` crossing lands near **98 °C** with `u_star ≈ 0.63` → runs hot at **~63% compute**.
- **1 water cooler** (`A = 10`) added to the 2‑EV‑fan build: `T* = 22 + (57−10)/0.5 = 116` → still
  throttles; add a helium cooler (`A = 60`) instead → `T* = 22 + max(0,57−60)/0.5 = 22 °C` → ice cold,
  100% compute.

## 6. Caveats (important — these are real behaviours in the code)

- **CPUs always run at max power.** The efficiency dropoff is therefore fixed at load = 1
  (`E_f = base − 0.2`). The variable that scales heat is **compute utilization** `u = allocatedCWU /
  maxCWU`, i.e. how much computation consumers are actually pulling — *not* CPU power.
- **Integer floors.** In code, per‑CPU heat and CWU are floored to whole numbers
  (`14.34 → 14`, `153.6 → 153`). Use the continuous formulas for tuning; they're within a fraction.
- **Ambient is biome‑based.** Overworld `≈ biomeTemp × 30 − 5` (plains ≈ 19, desert ≈ 55, snowy ≈ −5),
  Nether 70, End 5. Slide `T_a` to test hot/cold biomes.
- **One CPU type today.** Only the MV integrated circuit is registered (`CPURegistry.register()`), so `n`
  is copies of the same CPU. Add entries and `D`/`W_x` become sums over types.
- **Liquid = thermostat below 70 °C, full dump at ≥70 °C.** `A` in `Y_2 = C(T)+A` assumes the coolant
  supply keeps up (the right assumption for "is my cooling enough"). If coolant runs dry, `A → 0`.
- **RAM caps throughput** separately (`totalThroughput = Σ RAM`). If RAM is the bottleneck, actual
  utilization is `min(1 − S(T), RAM / maxCWU)`; heat scales with that smaller number.
- **Sag usually prevents explosion.** `S(T)` reaches 1 near 104 °C, driving produced heat to 0, so the
  machine tends to settle into a hot throttled state rather than blow up — barring a fast transient
  overshoot past 105.

---
*Source of truth: `CPURegistry`, `CoolingPartMachine`, `CoolantRegistry`, and
`MainframeMachine.GPCHandler`. If a constant here disagrees with the code, the code wins — update this
doc.*
