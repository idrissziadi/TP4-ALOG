# HISSAB — Spec de Refactoring : Composant CALC via EJB (TD2)

**Date :** 2026-05-12  
**Auteurs :** Ziadi, Boukakiou  
**Module :** SIQ1 — Architecture Logicielle (LAB4)  
**Version :** 3.0 — Remplacement exp4j par calculatrice TD2 + architecture @Remote

---

## 1. Contexte et problème

### 1.1 Ce qui existait (v2.0)

HISSAB v2.0 avait une architecture en WAR unique avec `CalcEJB` annotée `@Local` utilisant la bibliothèque externe `exp4j` pour évaluer des expressions infixes. Cette approche violait l'exigence principale du prof : **la réutilisabilité**.

| Problème | Impact |
|---|---|
| `CalcEJB` annotée `@Local` | Accessible seulement depuis le même WAR — pas réutilisable cross-projet |
| `exp4j` utilisé au lieu du TD2 | Ignore la contrainte "transformer votre calculatrice du TD2 en composant EJB" |
| WAR unique | Impossible de déployer le composant CALC indépendamment |
| `Evaluator` TD2 : postfixe seulement | Incompatible avec les expressions infixes extraites par OCR |
| `Evaluator` TD2 : `+` et `-` seulement | Ne gère pas `*` ni `/` |

### 1.2 Ce que le prof demande

L'énoncé dit explicitement :
> "Transformer votre calculatrice du TD 2 en un composant pour qu'il soit **réutilisable** en utilisant la technologie JEE (EJB)."

La réutilisabilité au sens JEE = un EJB déployé **séparément** sur le serveur d'application, accessible via `@Remote` depuis n'importe quelle application cliente sur le même GlassFish. Le dossier de référence `CalculEjjbRest` du prof confirme ce pattern : `CalculEjb` (module séparé) + `webtest` + `webrest` (deux clients distincts qui utilisent le même EJB via `@EJB CalculatorRemote`).

---

## 2. Solution retenue

### 2.1 Architecture deux modules Maven

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

### 2.2 Vue d'ensemble du déploiement

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
│  │  hissab.war                 │  ← déployé en second             │
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

---

## 3. Composants détaillés — MODULE 1 : calc-ejb

### 3.1 Interface @Remote — `ICalcRemote.java`

```java
package hissab.ejb;

import jakarta.ejb.Remote;

@Remote
public interface ICalcRemote {
    double evaluerExpression(String expression) throws IllegalArgumentException;
}
```

`@Remote` (et non `@Local`) permet l'accès depuis n'importe quelle application sur le même GlassFish — c'est le contrat de réutilisabilité.

---

### 3.2 EJB CALC — `CalcEJB.java`

```java
package hissab.ejb;

import jakarta.ejb.Stateless;
import calculator.interpreter.Evaluator;
import calculator.interpreter.InfixToPostfixConverter;
import java.util.Collections;

@Stateless
public class CalcEJB implements ICalcRemote {

    private static final InfixToPostfixConverter CONVERTER = new InfixToPostfixConverter();

    @Override
    public double evaluerExpression(String expressionStr) throws IllegalArgumentException {
        if (expressionStr == null || expressionStr.isBlank())
            throw new IllegalArgumentException("Expression vide");

        // 1. Normalisation des symboles Unicode
        String normalized = expressionStr
            .replace("×", "*").replace("÷", "/")
            .replace("−", "-").replaceAll("\\s+", " ").trim();

        // 2. Conversion infixe → postfixe (Shunting-Yard)
        String postfix = CONVERTER.convert(normalized);   // "5 2 6 * + 3 -"

        // 3. Évaluation via pattern Interpreter du TD2
        Evaluator evaluator = new Evaluator(postfix);
        return evaluator.interpret(Collections.emptyMap());
    }
}
```

---

### 3.3 Classes Interpreter copiées du TD2 (package `calculator.interpreter`)

Ces classes sont copiées telles quelles depuis `C:\Users\MICROSOFT PRO\ALOG\ZIADI_BOUKAKIOU_SIQ1\calculator\src\interpreter_jar\` avec un seul changement : migration de `int` vers `double` pour supporter la division décimale.

| Fichier | Origine | Changement |
|---|---|---|
| `Expression.java` | TD2 copié | `int interpret()` → `double interpret()` |
| `Variable.java` | TD2 copié | `int` → `double` |
| `NumberLiteral.java` | TD2 copié (existait mais inutilisé) | `int` → `double` |
| `Plus.java` | TD2 copié | `int` → `double` |
| `Minus.java` | TD2 copié | `int` → `double` |

---

### 3.4 Nouvelle classe — `Multiply.java`

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

---

### 3.5 Nouvelle classe — `Divide.java`

```java
package calculator.interpreter;

import java.util.Map;

public class Divide implements Expression {
    private final Expression left, right;

    public Divide(Expression l, Expression r) { left = l; right = r; }

    @Override
    public double interpret(Map<String, Expression> ctx) {
        double diviseur = right.interpret(ctx);
        if (diviseur == 0.0)
            throw new ArithmeticException("Division par zéro");
        return left.interpret(ctx) / diviseur;
    }
}
```

---

### 3.6 `Evaluator.java` — étendu du TD2

Deux modifications par rapport au TD2 :

1. **Support `*` et `/`** — deux nouveaux `case` dans la boucle de parsing
2. **Tokens numériques → `NumberLiteral`** — si le token est un nombre, on pousse `new NumberLiteral(valeur)` au lieu de `new Variable(token)`. Cela permet d'évaluer `"5 2 + "` directement sans passer de Map.

```java
package calculator.interpreter;

import java.util.Map;
import java.util.Stack;

public class Evaluator implements Expression {
    private Expression syntaxTree;

    public Evaluator(String postfixExpression) {
        Stack<Expression> stack = new Stack<>();
        for (String token : postfixExpression.trim().split("\\s+")) {
            switch (token) {
                case "+":
                    stack.push(new Plus(stack.pop(), stack.pop()));
                    break;
                case "-":
                    Expression right = stack.pop();
                    Expression left  = stack.pop();
                    stack.push(new Minus(left, right));
                    break;
                case "*":
                    stack.push(new Multiply(stack.pop(), stack.pop()));
                    break;
                case "/":
                    Expression rv = stack.pop();
                    Expression lv = stack.pop();
                    stack.push(new Divide(lv, rv));
                    break;
                default:
                    try {
                        stack.push(new NumberLiteral(Double.parseDouble(token)));
                    } catch (NumberFormatException e) {
                        stack.push(new Variable(token));
                    }
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

**Exemple de construction d'arbre pour `"5 2 6 * + 3 -"` :**
```
token "5"  → stack: [NL(5)]
token "2"  → stack: [NL(5), NL(2)]
token "6"  → stack: [NL(5), NL(2), NL(6)]
token "*"  → stack: [NL(5), Multiply(NL(2), NL(6))]
token "+"  → stack: [Plus(NL(5), Multiply(NL(2), NL(6)))]
token "3"  → stack: [Plus(...), NL(3)]
token "-"  → stack: [Minus(Plus(NL(5), Multiply(NL(2),NL(6))), NL(3))]
interpret → (5 + 2*6) - 3 = 17 - 3 = 14
```

---

### 3.7 Nouvelle classe — `InfixToPostfixConverter.java` (Shunting-Yard)

Algorithme de Dijkstra. Gère :
- Priorité des opérateurs : `*` et `/` (priorité 2) avant `+` et `-` (priorité 1)
- Parenthèses : `(5 + 2) * 6` → `5 2 + 6 *`
- Nombres à plusieurs chiffres : `12 + 34` → `12 34 +`
- Nombres décimaux : `3.14 * 2` → `3.14 2 *`

```java
package calculator.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

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

        // Tokenisation : découper en nombres (incl. décimaux) et opérateurs/parenthèses
        List<String> tokens = tokenize(infix);

        for (String token : tokens) {
            if (token.matches("-?\\d+(\\.\\d+)?")) {
                output.add(token);
            } else if (isOperator(token)) {
                while (!opStack.isEmpty() && isOperator(opStack.peek())
                        && priority(opStack.peek()) >= priority(token)) {
                    output.add(opStack.pop());
                }
                opStack.push(token);
            } else if (token.equals("(")) {
                opStack.push(token);
            } else if (token.equals(")")) {
                while (!opStack.isEmpty() && !opStack.peek().equals("(")) {
                    output.add(opStack.pop());
                }
                if (opStack.isEmpty())
                    throw new IllegalArgumentException("Parenthèse fermante sans ouvrante");
                opStack.pop(); // retirer "("
            } else {
                throw new IllegalArgumentException("Token inconnu : " + token);
            }
        }

        while (!opStack.isEmpty()) {
            String op = opStack.pop();
            if (op.equals("("))
                throw new IllegalArgumentException("Parenthèse ouvrante non fermée");
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

---

### 3.8 Classes TD2 conservées intactes

`ICalculator.java` et `Calculator.java` sont copiés sans modification dans `calculator/`. Le `Calculator` reste accessible via l'interface `ICalculator` (sum, product, factorial) — il n'est pas utilisé directement par `CalcEJB` pour l'évaluation d'expressions complètes, mais sa présence dans le module satisfait la contrainte "intégrer la calculatrice du TD2".

---

## 4. Composants détaillés — MODULE 2 : hissab-war

### 4.1 `HissabRestService.java` — seul changement

```java
// AVANT
@EJB
private ICalcLocal calc;

// APRÈS
@EJB
private ICalcRemote calc;
```

Tout le reste (endpoints, logique OCR, historique) est inchangé.

### 4.2 EJBs et entités inchangés

- `HistoriqueEJB` : `@Stateless @Local` — pas de changement
- `CalculHistorique` : entité JPA — pas de changement
- `IHistoriqueLocal` : interface `@Local` — pas de changement
- `OcrUtil`, `ResultatDTO`, `ApplicationConfig` — inchangés

---

## 5. Flux de traitement complet

```
[Élève soumet image]
    ↓
OcrUtil.extraireTexte()
    → "5 + 2 × 6 − 3"
    ↓
CalcEJB.evaluerExpression()
    → normalize()           → "5 + 2 * 6 - 3"
    → Shunting-Yard.convert → "5 2 6 * + 3 -"
    → Evaluator("<postfix>") : construit l'arbre Interpreter
        Minus(
            Plus(NL(5), Multiply(NL(2), NL(6))),
            NL(3)
        )
    → .interpret({})        → 14.0
    ↓
HistoriqueEJB.sauvegarder("5 + 2 * 6 - 3", 14.0)
    ↓
ResultatDTO { expression:"5 + 2 * 6 - 3", resultat:14.0, succes:true }
    ↓
[Client reçoit JSON]
```

---

## 6. Configuration Maven

### 6.1 `pom.xml` parent (HISSAB-root)

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

### 6.2 `pom.xml` de calc-ejb

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
          <generateClient>true</generateClient> <!-- génère calc-ejb-client.jar avec les interfaces -->
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 6.3 `pom.xml` de hissab-war

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

**Explication `classifier=client` :** le plugin `maven-ejb-plugin` avec `generateClient=true` génère automatiquement un JAR `calc-ejb-client.jar` contenant seulement les interfaces (`ICalcRemote`). `hissab-war` dépend de ce JAR client (scope=provided) pour la compilation — il n'embarque pas le code EJB dans le WAR.

---

## 7. Déploiement sur GlassFish 7

```bash
# Ordre obligatoire : d'abord le composant, ensuite le client

# 1. Construire les deux modules
mvn clean package

# 2. Déployer le composant CALC (EJB-JAR)
asadmin deploy calc-ejb/target/calc-ejb-1.0.jar

# 3. Déployer l'application HISSAB (WAR)
asadmin deploy hissab-war/target/hissab-war-1.0.war

# Vérification
asadmin list-applications
# → calc-ejb   <ejb>
# → hissab-war <web>
```

**Preuve de réutilisabilité :** n'importe quelle autre application sur GlassFish peut maintenant faire :
```java
@EJB
private ICalcRemote calc;
// → injecte automatiquement CalcEJB déployé dans calc-ejb.jar
```
ou via JNDI :
```java
ctx.lookup("java:global/calc-ejb/CalcEJB!hissab.ejb.ICalcRemote")
```

---

## 8. Gestion des erreurs

| Situation | Comportement |
|---|---|
| Expression null ou vide | `IllegalArgumentException("Expression vide")` |
| Token inconnu dans Shunting-Yard | `IllegalArgumentException("Token inconnu : X")` |
| Parenthèse non fermée | `IllegalArgumentException("Parenthèse ouvrante non fermée")` |
| Division par zéro | `ArithmeticException("Division par zéro")` dans `Divide.interpret()` |
| OCR ne retourne rien | `HissabRestService` retourne HTTP 400 (comportement inchangé) |

`CalcEJB.evaluerExpression()` propage `IllegalArgumentException` — `HissabRestService` l'attrape et retourne HTTP 400 avec message d'erreur (comportement inchangé de v2.0).

---

## 9. Ce qui NE change PAS

- `HistoriqueEJB`, `CalculHistorique`, `IHistoriqueLocal`
- `persistence.xml`, `glassfish-resources.xml`, `web.xml`
- `OcrUtil`, `ApplicationConfig`, `ResultatDTO`
- `index.html`, `hissab.js`
- Base de données Derby, table `CALC_HISTORIQUE`
- Endpoints REST : `POST /api/hissab/calculer`, `GET /api/hissab/historique`

---

## 10. Résumé des fichiers à créer / modifier

### Nouveaux fichiers dans `calc-ejb/`

| Fichier | Type |
|---|---|
| `calc-ejb/pom.xml` | Maven EJB module |
| `calculator/ICalculator.java` | Copié TD2 |
| `calculator/Calculator.java` | Copié TD2 |
| `calculator/interpreter/Expression.java` | Copié TD2, `int`→`double` |
| `calculator/interpreter/Variable.java` | Copié TD2, `int`→`double` |
| `calculator/interpreter/NumberLiteral.java` | Copié TD2, `int`→`double` |
| `calculator/interpreter/Plus.java` | Copié TD2, `int`→`double` |
| `calculator/interpreter/Minus.java` | Copié TD2, `int`→`double` |
| `calculator/interpreter/Multiply.java` | **Nouveau** |
| `calculator/interpreter/Divide.java` | **Nouveau** |
| `calculator/interpreter/Evaluator.java` | **Étendu** (`*`, `/`, literals) |
| `calculator/interpreter/InfixToPostfixConverter.java` | **Nouveau** (Shunting-Yard) |
| `hissab/ejb/ICalcRemote.java` | **Nouveau** `@Remote` |
| `hissab/ejb/CalcEJB.java` | **Nouveau** (utilise TD2 + Converter) |

### Fichiers modifiés dans `hissab-war/`

| Fichier | Changement |
|---|---|
| `pom.xml` (root) | Ajouter structure multi-module + supprimer exp4j |
| `hissab-war/pom.xml` | Dépend de `calc-ejb` client JAR |
| `HissabRestService.java` | `ICalcLocal` → `ICalcRemote` |

### Fichiers supprimés

| Fichier | Raison |
|---|---|
| `hissab/ejb/ICalcLocal.java` | Remplacé par `ICalcRemote` |
| `target/.../exp4j-0.4.8.jar` | Dépendance supprimée |

---

## 11. Plan de réalisation

| # | Étape | Fichiers |
|---|---|---|
| 1 | Créer le parent `pom.xml` | `HISSAB-root/pom.xml` |
| 2 | Créer module `calc-ejb` avec son `pom.xml` | `calc-ejb/pom.xml` |
| 3 | Copier les classes TD2 dans `calc-ejb`, migrer `int`→`double` | Expression, Plus, Minus, Variable, NumberLiteral, Evaluator |
| 4 | Ajouter `Multiply.java` et `Divide.java` | Nouveaux |
| 5 | Étendre `Evaluator` pour `*`, `/` et literals numériques | Modifié |
| 6 | Implémenter `InfixToPostfixConverter` (Shunting-Yard) | Nouveau |
| 7 | Créer `ICalcRemote` `@Remote` et `CalcEJB` | Nouveaux |
| 8 | Restructurer `hissab-war` : supprimer exp4j, ajouter dépendance client | `pom.xml` |
| 9 | Modifier `HissabRestService` : `ICalcLocal` → `ICalcRemote` | Modifié |
| 10 | Build Maven : `mvn clean package` | Vérification |
| 11 | Déployer `calc-ejb.jar` sur GlassFish | Test injection |
| 12 | Déployer `hissab.war` sur GlassFish | Test end-to-end |
| 13 | Test complet : OCR → calcul → historique | Validation |
| 14 | ZIP source + vidéo démo (3 min max) | Livrable |

**Total estimé : ~4h**
