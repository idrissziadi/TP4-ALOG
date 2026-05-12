# HISSAB — Vérificateur de calculs pour classes primaires

**Auteurs :** Ziadi, Boukakiou  
**Module :** SIQ1 — Architecture Logicielle (LAB4)  
**Version :** 3.0  
**Serveur :** GlassFish 7.0.x / Jakarta EE 10

---

## Présentation

HISSAB est un système web qui aide les élèves de primaire à vérifier des calculs mathématiques. L'élève soumet une image ou un PDF contenant une expression (ex : `5 + 2 × 6 − 3`), le système extrait l'expression via OCR, la calcule et retourne le résultat. Chaque calcul est enregistré en base de données.

---

## Architecture — Deux modules Maven (v3.0)

La calculatrice du TD2 est transformée en **composant EJB réutilisable** (`calc-ejb`), déployé indépendamment sur GlassFish. L'application web HISSAB (`hissab-war`) l'utilise via l'interface `@Remote`.

```
HISSAB-root/                              ← parent Maven (packaging=pom)
├── pom.xml
├── calc-ejb/                             ← MODULE 1 : composant CALC (EJB-JAR)
│   ├── pom.xml                           (packaging=ejb)
│   └── src/main/java/
│       ├── calculator/
│       │   ├── ICalculator.java          ← TD2 (interface, inchangée)
│       │   └── Calculator.java           ← TD2 (sum/product/factorial, inchangée)
│       ├── calculator/interpreter/
│       │   ├── Expression.java           ← TD2 copié (interface)
│       │   ├── Variable.java             ← TD2 copié
│       │   ├── NumberLiteral.java        ← TD2 copié (existait, maintenant utilisé)
│       │   ├── Plus.java                 ← TD2 copié (migré double)
│       │   ├── Minus.java                ← TD2 copié (migré double)
│       │   ├── Multiply.java             ← NOUVEAU (pattern Interpreter)
│       │   ├── Divide.java               ← NOUVEAU (pattern Interpreter, garde /0)
│       │   ├── Evaluator.java            ← TD2 ÉTENDU (* / et literals numériques)
│       │   └── InfixToPostfixConverter.java ← NOUVEAU (algorithme Shunting-Yard)
│       └── hissab/ejb/
│           ├── ICalcRemote.java          ← @Remote (remplace ICalcLocal)
│           └── CalcEJB.java              ← @Stateless, utilise TD2 Calculator
│
└── hissab-war/                           ← MODULE 2 : application web (WAR)
    ├── pom.xml                           (packaging=war, dépend de calc-ejb)
    └── src/main/java/hissab/
        ├── ejb/
        │   ├── ICalcRemote.java          ← même interface @Remote (contrat partagé)
        │   ├── HistoriqueEJB.java        ← @Stateless @Local, inchangé
        │   └── CalculHistorique.java     ← @Entity JPA, inchangée
        └── web/
            ├── HissabRestService.java    ← @EJB ICalcRemote (remplace ICalcLocal)
            ├── OcrUtil.java              ← inchangé
            ├── ApplicationConfig.java    ← inchangé
            └── ResultatDTO.java          ← inchangé
```

### Vue d'ensemble du déploiement GlassFish

```
┌──────────────────────────────────────────────────────────────────┐
│                        GlassFish 7                                │
│                                                                    │
│  ┌─────────────────────────────┐                                  │
│  │  calc-ejb.jar               │  ← déployé en premier            │
│  │  (composant CALC autonome)  │                                  │
│  │                             │                                  │
│  │  CalcEJB  @Stateless        │◄── @EJB (injection depuis WAR)   │
│  │  ICalcRemote  @Remote       │◄── JNDI lookup (autres applis)   │
│  └─────────────────────────────┘                                  │
│                                                                    │
│  ┌─────────────────────────────┐                                  │
│  │  HISSAB.war                 │  ← déployé en second             │
│  │                             │                                  │
│  │  HissabRestService          │                                  │
│  │    @EJB ICalcRemote ────────┼──────────────► CalcEJB           │
│  │    @EJB IHistoriqueLocal    │                                  │
│  │                             │                                  │
│  │  HistoriqueEJB  @Local      │                                  │
│  │  → Derby (JPA)              │                                  │
│  └─────────────────────────────┘                                  │
│                                                                    │
│  ┌─────────────────────────────┐                                  │
│  │  Autre app (futur)          │  ← réutilisation sans HISSAB    │
│  │    @EJB ICalcRemote ────────┼──────────────► CalcEJB           │
│  └─────────────────────────────┘                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Flux de traitement

```
[Image/PDF]
    → OCR (OCR.space) → "5 + 2 × 6 − 3"
    → normalisation   → "5 + 2 * 6 - 3"
    → Shunting-Yard   → "5 2 6 * + 3 -"  (postfixe)
    → Evaluator TD2   → Minus(Plus(5, Multiply(2,6)), 3) = 14.0
    → Derby (JPA)     → historique enregistré
    → JSON            → { expression, resultat, succes }
```

### Réutilisabilité

Le composant CALC est déployé une fois, accessible depuis n'importe quelle application sur le même GlassFish :

```java
// Dans n'importe quelle autre application
@EJB
private ICalcRemote calc;  // injection automatique depuis calc-ejb.jar

// Ou via JNDI
ctx.lookup("java:global/calc-ejb/CalcEJB!hissab.ejb.ICalcRemote")
```

---

## Prérequis

- Java 17+ (vérifier : `java -version`)
- Maven 3.8+ (vérifier : `mvn -version`)
- GlassFish 7.0.x installé (ex : `C:\glassfish7\`)
- La clé OCR est déjà intégrée dans `OcrUtil.java` — aucune configuration nécessaire

---

## Lancer le projet — Étapes complètes

### 1. Ajouter `asadmin` au PATH (une seule fois)

Sur Windows, ajouter le répertoire `bin` de GlassFish à la variable PATH :

```
C:\glassfish7\bin
```

Ou utiliser le chemin complet à chaque commande :
```cmd
C:\glassfish7\bin\asadmin <commande>
```

---

### 2. Démarrer GlassFish

```cmd
asadmin start-domain
```

GlassFish démarre sur :
- Application : `http://localhost:8080`
- Console admin : `http://localhost:4848`

Vérifier que GlassFish est bien démarré :
```cmd
asadmin list-domains
```

---

### 3. Compiler et packager le projet

```cmd
cd C:\Users\MICROSOFT PRO\ALOG\ZIADI_BOUKAKIOU_SIQ1_LAB4\HISSAB
mvn clean package -DskipTests
```

Artefacts générés :
```
calc-ejb\target\calc-ejb-1.0.jar        ← composant CALC (EJB-JAR)
calc-ejb\target\calc-ejb-1.0-client.jar ← interfaces uniquement
hissab-war\target\HISSAB.war            ← application web
```

---

### 4. Déployer sur GlassFish (ordre obligatoire)

> Le composant CALC **doit** être déployé avant HISSAB — HISSAB en dépend via `@Remote`.

```cmd
cd C:\Users\MICROSOFT PRO\ALOG\ZIADI_BOUKAKIOU_SIQ1_LAB4\HISSAB

:: Étape 4a — Déployer le composant CALC
asadmin deploy calc-ejb\target\calc-ejb-1.0.jar

:: Étape 4b — Déployer l'application HISSAB
asadmin deploy hissab-war\target\HISSAB.war
```

Vérifier que les deux sont déployés :
```cmd
asadmin list-applications
```
Résultat attendu :
```
calc-ejb    <ejb>
hissab-war  <web>
```

---

### 5. Ouvrir l'application

Navigateur : **`http://localhost:8080/HISSAB/`**

---

### 6. Tester l'API REST

**Test avec expression manuelle (curl) :**
```cmd
curl -X POST http://localhost:8080/HISSAB/api/hissab/calculer ^
  -F "expression=5 + 2 * 6 - 3"
```

Réponse attendue :
```json
{"expression":"5 + 2 * 6 - 3","resultat":14.0,"succes":true,"message":"Calcul réussi"}
```

**Test avec image (curl) :**
```cmd
curl -X POST http://localhost:8080/HISSAB/api/hissab/calculer ^
  -F "fichier=@C:\chemin\vers\image.png"
```

**Consulter l'historique :**
```cmd
curl http://localhost:8080/HISSAB/api/hissab/historique
```

---

### 7. Redéployer après modification du code

```cmd
:: Recompiler
mvn clean package -DskipTests

:: Redéployer les deux modules (même ordre)
asadmin undeploy hissab-war
asadmin undeploy calc-ejb
asadmin deploy calc-ejb\target\calc-ejb-1.0.jar
asadmin deploy hissab-war\target\HISSAB.war
```

---

### 8. Arrêter GlassFish

```cmd
asadmin stop-domain
```

---

### Récapitulatif des commandes utiles

| Commande | Action |
|---|---|
| `asadmin start-domain` | Démarrer GlassFish |
| `asadmin stop-domain` | Arrêter GlassFish |
| `asadmin list-applications` | Lister les applications déployées |
| `asadmin deploy calc-ejb\target\calc-ejb-1.0.jar` | Déployer le composant CALC |
| `asadmin deploy hissab-war\target\HISSAB.war` | Déployer HISSAB |
| `asadmin undeploy calc-ejb` | Désinstaller CALC |
| `asadmin undeploy hissab-war` | Désinstaller HISSAB |
| `asadmin list-domains` | Vérifier l'état de GlassFish |
| `mvn clean package -DskipTests` | Compiler et packager |

---

## Configuration Maven

### `pom.xml` parent (HISSAB-root)

```xml
<project>
  <groupId>hissab</groupId>
  <artifactId>HISSAB-root</artifactId>
  <version>1.0</version>
  <packaging>pom</packaging>

  <modules>
    <module>calc-ejb</module>
    <module>hissab-war</module>
  </modules>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>jakarta.platform</groupId>
        <artifactId>jakarta.jakartaee-api</artifactId>
        <version>10.0.0</version>
        <scope>provided</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

### `calc-ejb/pom.xml`

```xml
<project>
  <parent>
    <groupId>hissab</groupId>
    <artifactId>HISSAB-root</artifactId>
    <version>1.0</version>
  </parent>

  <artifactId>calc-ejb</artifactId>
  <packaging>ejb</packaging>

  <dependencies>
    <dependency>
      <groupId>jakarta.platform</groupId>
      <artifactId>jakarta.jakartaee-api</artifactId>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-ejb-plugin</artifactId>
        <version>3.2.1</version>
        <configuration>
          <ejbVersion>3.2</ejbVersion>
          <generateClient>true</generateClient>
          <!-- génère calc-ejb-client.jar avec les interfaces uniquement -->
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### `hissab-war/pom.xml`

```xml
<project>
  <parent>
    <groupId>hissab</groupId>
    <artifactId>HISSAB-root</artifactId>
    <version>1.0</version>
  </parent>

  <artifactId>hissab-war</artifactId>
  <packaging>war</packaging>

  <dependencies>
    <dependency>
      <groupId>jakarta.platform</groupId>
      <artifactId>jakarta.jakartaee-api</artifactId>
      <scope>provided</scope>
    </dependency>
    <!-- Interface @Remote uniquement — le JAR EJB est déployé séparément -->
    <dependency>
      <groupId>hissab</groupId>
      <artifactId>calc-ejb</artifactId>
      <version>1.0</version>
      <classifier>client</classifier>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-war-plugin</artifactId>
        <version>3.4.0</version>
        <configuration>
          <failOnMissingWebXml>false</failOnMissingWebXml>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

> `classifier=client` : `maven-ejb-plugin` avec `generateClient=true` génère automatiquement `calc-ejb-client.jar` contenant seulement les interfaces. `hissab-war` compile contre ce JAR (scope=provided) sans embarquer le code EJB dans le WAR.

---

## Build

```cmd
mvn clean package -DskipTests
```

Génère :
- `calc-ejb/target/calc-ejb-1.0.jar` — composant CALC (EJB-JAR)
- `calc-ejb/target/calc-ejb-1.0-client.jar` — interfaces uniquement
- `hissab-war/target/HISSAB.war` — application web

---

## Déploiement GlassFish (résumé)

> **Ordre obligatoire** : déployer `calc-ejb` avant `hissab-war`.

```cmd
asadmin deploy calc-ejb\target\calc-ejb-1.0.jar
asadmin deploy hissab-war\target\HISSAB.war
```

L'application est accessible à : `http://localhost:8080/HISSAB/`

---

## API REST

### POST `/api/hissab/calculer`

Calcule une expression arithmétique (depuis image, PDF ou texte).

```
Content-Type: multipart/form-data
Paramètres :
  fichier    (optionnel) : image PNG/JPG ou PDF
  expression (optionnel) : texte saisi manuellement
```

Réponse succès :
```json
{ "expression": "5 + 2 * 6 - 3", "resultat": 14.0, "succes": true, "message": "Calcul réussi" }
```

Réponse erreur :
```json
{ "succes": false, "message": "Expression invalide : ..." }
```

### GET `/api/hissab/historique`

Retourne la liste des calculs enregistrés (du plus récent au plus ancien).

```json
[
  { "id": 1, "expression": "5 + 2 * 6 - 3", "resultat": 14.0, "dateCalcul": "..." }
]
```

---

## EJBs

| EJB | Annotation | Interface | Rôle |
|---|---|---|---|
| `CalcEJB` | `@Stateless` | `@Remote ICalcRemote` | Évalue les expressions via le pattern Interpreter (TD2) + Shunting-Yard |
| `HistoriqueEJB` | `@Stateless` | `@Local IHistoriqueLocal` | Persiste les calculs dans Derby via JPA |

### Pourquoi @Remote pour CalcEJB ?

`@Remote` (vs `@Local`) rend le composant CALC accessible depuis des applications **différentes** sur le même serveur. C'est la contrainte "réutilisabilité" de l'énoncé : une autre appli peut injecter `@EJB ICalcRemote` sans avoir besoin de HISSAB.

---

## Composant CALC — logique interne

### Conversion infixe → postfixe (Shunting-Yard)

```
"5 + 2 * 6 - 3"  →  "5 2 6 * + 3 -"
"(5 + 2) * 6"    →  "5 2 + 6 *"
```

Priorités : `*` et `/` = 2, `+` et `-` = 1. Supporte les parenthèses et les nombres décimaux.

#### `InfixToPostfixConverter.java`

```java
package calculator.interpreter;

import java.util.*;

public class InfixToPostfixConverter {

    private int priority(String op) {
        return (op.equals("*") || op.equals("/")) ? 2 : 1;
    }

    private boolean isOperator(String s) {
        return s.equals("+") || s.equals("-") || s.equals("*") || s.equals("/");
    }

    public String convert(String infix) {
        List<String> output = new ArrayList<>();
        Stack<String> opStack = new Stack<>();
        for (String token : tokenize(infix)) {
            if (token.matches("-?\\d+(\\.\\d+)?")) {
                output.add(token);
            } else if (isOperator(token)) {
                while (!opStack.isEmpty() && isOperator(opStack.peek())
                        && priority(opStack.peek()) >= priority(token))
                    output.add(opStack.pop());
                opStack.push(token);
            } else if (token.equals("(")) {
                opStack.push(token);
            } else if (token.equals(")")) {
                while (!opStack.isEmpty() && !opStack.peek().equals("("))
                    output.add(opStack.pop());
                if (opStack.isEmpty())
                    throw new IllegalArgumentException("Parenthèse fermante sans ouvrante");
                opStack.pop();
            } else {
                throw new IllegalArgumentException("Token inconnu : " + token);
            }
        }
        while (!opStack.isEmpty()) {
            String op = opStack.pop();
            if (op.equals("(")) throw new IllegalArgumentException("Parenthèse ouvrante non fermée");
            output.add(op);
        }
        return String.join(" ", output);
    }

    private List<String> tokenize(String infix) {
        List<String> tokens = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        for (char c : infix.toCharArray()) {
            if (Character.isDigit(c) || c == '.') {
                num.append(c);
            } else {
                if (num.length() > 0) { tokens.add(num.toString()); num.setLength(0); }
                if (c != ' ') tokens.add(String.valueOf(c));
            }
        }
        if (num.length() > 0) tokens.add(num.toString());
        return tokens;
    }
}
```

### Pattern Interpreter étendu (TD2)

```
Evaluator("5 2 6 * + 3 -")
  Minus(
    Plus(NL(5), Multiply(NL(2), NL(6))),
    NL(3)
  )
  .interpret({}) → 14.0
```

Construction de l'arbre pour `"5 2 6 * + 3 -"` :
```
token "5"  → stack: [NL(5)]
token "2"  → stack: [NL(5), NL(2)]
token "6"  → stack: [NL(5), NL(2), NL(6)]
token "*"  → stack: [NL(5), Multiply(NL(2), NL(6))]
token "+"  → stack: [Plus(NL(5), Multiply(NL(2), NL(6)))]
token "3"  → stack: [Plus(...), NL(3)]
token "-"  → stack: [Minus(Plus(NL(5), Multiply(NL(2),NL(6))), NL(3))]
interpret  → (5 + 2×6) − 3 = 17 − 3 = 14.0
```

Classes du pattern Interpreter : `Expression` (interface), `Plus`, `Minus`, `Multiply` (nouveau), `Divide` (nouveau), `Variable`, `NumberLiteral`.  
Migration : `int` → `double` pour supporter les divisions décimales (`10 / 4 = 2.5`).

#### `Evaluator.java` (étendu du TD2)

```java
package calculator.interpreter;

import java.util.*;

public class Evaluator implements Expression {
    private Expression syntaxTree;

    public Evaluator(String postfixExpression) {
        Stack<Expression> stack = new Stack<>();
        for (String token : postfixExpression.trim().split("\\s+")) {
            switch (token) {
                case "+": stack.push(new Plus(stack.pop(), stack.pop())); break;
                case "-":
                    Expression right = stack.pop(); Expression left = stack.pop();
                    stack.push(new Minus(left, right)); break;
                case "*": stack.push(new Multiply(stack.pop(), stack.pop())); break;
                case "/":
                    Expression rv = stack.pop(); Expression lv = stack.pop();
                    stack.push(new Divide(lv, rv)); break;
                default:
                    try { stack.push(new NumberLiteral(Double.parseDouble(token))); }
                    catch (NumberFormatException e) { stack.push(new Variable(token)); }
            }
        }
        syntaxTree = stack.pop();
    }

    @Override
    public double interpret(Map<String, Expression> context) {
        return syntaxTree.interpret(context);
    }
}
```

#### `Multiply.java` (nouveau)

```java
package calculator.interpreter;
import java.util.Map;

public class Multiply implements Expression {
    private final Expression left, right;
    public Multiply(Expression l, Expression r) { left = l; right = r; }

    @Override
    public double interpret(Map<String, Expression> ctx) {
        return left.interpret(ctx) * right.interpret(ctx);
    }
}
```

#### `Divide.java` (nouveau — garde division par zéro)

```java
package calculator.interpreter;
import java.util.Map;

public class Divide implements Expression {
    private final Expression left, right;
    public Divide(Expression l, Expression r) { left = l; right = r; }

    @Override
    public double interpret(Map<String, Expression> ctx) {
        double diviseur = right.interpret(ctx);
        if (diviseur == 0.0) throw new ArithmeticException("Division par zéro");
        return left.interpret(ctx) / diviseur;
    }
}
```

#### `ICalcRemote.java` et `CalcEJB.java`

```java
// ICalcRemote.java — contrat partagé entre les modules
@Remote
public interface ICalcRemote {
    double evaluerExpression(String expression) throws IllegalArgumentException;
}

// CalcEJB.java — implémentation @Stateless
@Stateless
public class CalcEJB implements ICalcRemote {
    private static final InfixToPostfixConverter CONVERTER = new InfixToPostfixConverter();

    @Override
    public double evaluerExpression(String expressionStr) throws IllegalArgumentException {
        if (expressionStr == null || expressionStr.isBlank())
            throw new IllegalArgumentException("Expression vide");

        // Normalisation des symboles Unicode (OCR peut retourner × ÷ −)
        String normalized = expressionStr
            .replace("×", "*").replace("÷", "/")
            .replace("−", "-").replaceAll("\\s+", " ").trim();

        // Shunting-Yard → postfixe, puis Interpreter TD2 → résultat
        String postfix = CONVERTER.convert(normalized);
        return new Evaluator(postfix).interpret(Collections.emptyMap());
    }
}
```

---

## Gestion des erreurs

| Situation | Comportement |
|---|---|
| Expression null ou vide | `IllegalArgumentException("Expression vide")` dans `CalcEJB` |
| Token inconnu dans Shunting-Yard | `IllegalArgumentException("Token inconnu : X")` |
| Parenthèse non fermée | `IllegalArgumentException("Parenthèse ouvrante non fermée")` |
| Division par zéro | `ArithmeticException("Division par zéro")` dans `Divide.interpret()` |
| OCR ne retourne rien | HTTP 400 + message explicatif (inchangé depuis v2.0) |
| EJB enveloppe l'exception | `HissabRestService` déballe la cause (`getCause()`) avant de répondre |

`CalcEJB.evaluerExpression()` propage `IllegalArgumentException` — `HissabRestService` l'attrape, déballe la cause réelle, et retourne HTTP 400 avec le message d'erreur.

---

## Base de données Derby

Table créée automatiquement par EclipseLink au premier déploiement :

```sql
CREATE TABLE CALC_HISTORIQUE (
    ID          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    EXPRESSION  VARCHAR(500) NOT NULL,
    RESULTAT    DOUBLE       NOT NULL,
    DATE_CALCUL TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

DataSource Derby embarqué configuré dans `glassfish-resources.xml` — aucune configuration manuelle nécessaire.

---

## Comparaison v2.0 → v3.0

| | v2.0 | v3.0 |
|---|---|---|
| Architecture | WAR unique | 2 modules Maven (EJB-JAR + WAR) |
| Interface EJB | `@Local` | `@Remote` |
| Moteur de calcul | `exp4j` (bibliothèque externe) | Pattern Interpreter TD2 étendu |
| Support infixe | Natif exp4j | `InfixToPostfixConverter` (Shunting-Yard) |
| Opérateurs | +, -, *, / | +, -, *, / (Multiply et Divide ajoutés) |
| Réutilisabilité | Non (WAR couplé) | Oui (EJB-JAR déployé séparément) |

---

## Structure complète du projet

```
HISSAB/
├── pom.xml                          ← parent (packaging=pom)
├── calc-ejb/
│   ├── pom.xml                      ← packaging=ejb, maven-ejb-plugin
│   └── src/main/java/
│       ├── calculator/
│       │   ├── ICalculator.java
│       │   ├── Calculator.java
│       │   └── interpreter/
│       │       ├── Expression.java
│       │       ├── Variable.java
│       │       ├── NumberLiteral.java
│       │       ├── Plus.java
│       │       ├── Minus.java
│       │       ├── Multiply.java
│       │       ├── Divide.java
│       │       ├── Evaluator.java
│       │       └── InfixToPostfixConverter.java
│       └── hissab/ejb/
│           ├── ICalcRemote.java
│           └── CalcEJB.java
├── hissab-war/
│   ├── pom.xml                      ← packaging=war, dépend de calc-ejb client
│   └── src/main/
│       ├── java/hissab/
│       │   ├── ejb/
│       │   │   ├── ICalcRemote.java
│       │   │   ├── IHistoriqueLocal.java
│       │   │   ├── HistoriqueEJB.java
│       │   │   └── CalculHistorique.java
│       │   └── web/
│       │       ├── ApplicationConfig.java
│       │       ├── HissabRestService.java
│       │       ├── OcrUtil.java
│       │       └── ResultatDTO.java
│       ├── resources/META-INF/
│       │   └── persistence.xml
│       └── webapp/
│           ├── index.html
│           ├── hissab.js
│           └── WEB-INF/
│               ├── web.xml
│               └── glassfish-resources.xml
└── docs/superpowers/specs/
    └── 2026-05-12-hissab-calc-ejb-design.md
```

---

## Points d'attention — Jakarta EE 10

GlassFish 7 = Jakarta EE 10 → **tous les imports sont `jakarta.*`**, jamais `javax.*`.

| Mauvais | Correct |
|---|---|
| `javax.ejb.Stateless` | `jakarta.ejb.Stateless` |
| `javax.ejb.Remote` | `jakarta.ejb.Remote` |
| `javax.ws.rs.Path` | `jakarta.ws.rs.Path` |
| `javax.persistence.*` | `jakarta.persistence.*` |

Provider JPA : **EclipseLink** (fourni par GlassFish 7) — ne pas utiliser les propriétés `hibernate.*`.
