# CompEngSumativeTest
## Fusion Reactor Game Demos

Seven nuclear-themed arcade game demos written in Java (Swing). Each is a short, playable proof-of-concept that remixes a classic arcade game with a scientific fusion-reactor setting.

---

### How to compile & run

```bash
# Compile all demos (requires Java 17+)
./compile.sh

# Launch the game-selection menu
./run.sh
```

You can also run any individual demo directly:

```bash
cd demos
javac *.java        # compile once
java QFusion        # Q-Fusion
java FusionField    # Fusion Field
java ReactorRun     # Reactor Run
java IsotopeCrossing
java NeutronDefenders
java MeltdownClimb
java FusionSwarm
java Launcher       # full menu
```

---

### The seven demos

| Demo | Classic | Concept |
|------|---------|---------|
| **Q-Fusion** | Q-Bert | Hop across an isometric pyramid of reactor nodes, cycling each from *Offline → Charging → Stabilized*. Neutron-burst enemies hop down; avoid them or lose a life. Win when every node is stabilized. |
| **Fusion Field** | Asteroids | Pilot a maintenance craft in a failing tokamak. Large plasma fragments split into smaller pieces when hit. A heat meter fills when debris escapes; survive 5 waves to win. |
| **Reactor Run** | Pac-Man | Collect fuel cells in a 19×19 facility maze while four radiation-cloud enemies chase you. Power cells grant temporary lead shielding and let you eat the clouds. |
| **Isotope Crossing** | Frogger | Guide a lab assistant across conveyor belts and coolant streams. Ride maintenance drones across the middle section; deliver isotopes to all five containment chambers. |
| **Neutron Defenders** | Space Invaders | Defend the fusion core from 4 × 10 descending invader formations. Switch between three weapon modes: Magnetic Pulse, Coolant Burst, and Containment Beam. |
| **Meltdown Climb** | Donkey Kong | Climb four platform levels to reach the emergency-shutdown panel. A sabotage robot at the top drops radioactive waste drums and plasma bolts. A meltdown timer counts down. |
| **Fusion Swarm** | Galaga | Enemy ion swarms fly in formation and dive-bomb your craft. A boss enemy can fire a tractor beam to capture your ship — destroy it to recover and unlock dual firepower. |

---

### Controls (all games)

| Key | Action |
|-----|--------|
| **Arrow keys** | Move / aim |
| **SPACE** | Jump / fire |
| **Q E Z C** | Q-Fusion diagonal hops |
| **1 2 3** | Weapon mode (Neutron Defenders) |
| **R** | Restart current game |

---

### OOP design highlights

Each demo demonstrates core Java OOP concepts:

- **Inheritance** – each game panel extends `JPanel`
- **Encapsulation** – game objects use static inner classes (`Enemy`, `Bullet`, `Fragment`, `Ghost`, `Barrel`, `Shield`, …)
- **Polymorphism** – `paintComponent` is overridden in every game panel
- **State machines** – enemies have `EnemyState` enum (FORMATION / DIVING / RETURNING) in Fusion Swarm; node states in Q-Fusion; ghost scared/normal in Reactor Run
- **Event-driven** – `KeyListener` + `javax.swing.Timer` game loop in all demos

---

### File structure

```
demos/
├── QFusion.java          Q-Bert style
├── FusionField.java      Asteroids style
├── ReactorRun.java       Pac-Man style
├── IsotopeCrossing.java  Frogger style
├── NeutronDefenders.java Space Invaders style
├── MeltdownClimb.java    Donkey Kong style
├── FusionSwarm.java      Galaga style
└── Launcher.java         Game-selection menu
compile.sh               Compile helper
run.sh                   Launch menu helper
```

---

### Requirements

- Java 17 or later (`javac -version` to check)
- No external libraries – standard JDK only
