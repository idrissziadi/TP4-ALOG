# HISSAB — Document de Conception (Spec)

**Date :** 2026-05-12  
**Auteurs :** Ziadi, Boukakiou  
**Module :** SIQ1 — Architecture Logicielle (LAB4)  
**Version :** 4.0  
**Serveur :** GlassFish 7.0.x / Jakarta EE 10

---

## 1. Objectif du projet

HISSAB est un système web qui aide des élèves de primaire à vérifier des calculs mathématiques. L'élève soumet une image ou un PDF contenant une expression (ex : `5 + 2 × 6 − 3`), le système extrait l'expression, la calcule automatiquement et retourne le résultat. Chaque calcul est enregistré dans un historique persistant.

---

## 2. Contraintes du TP

- Minimum **3 EJBs** déployés (Enterprise JavaBeans)
- Architecture **à base de composants** (JEE / GlassFish 7.0.25)
- **Calculatrice du TD2 transformée en composant EJB `@Remote` réutilisable**
- Client : **HTML + JavaScript** avec API REST (JAX-RS)
- Base de données : **Derby** (intégrée à GlassFish)
- Livrables : ZIP du code source + vidéo de démo (max 3 min)

---

## 3. Architecture — Vue d'ensemble

**Modèle de déploiement : 2 modules Maven** — un EJB-JAR (`calc-ejb`) et un WAR (`hissab-war`), déployés séparément sur GlassFish.

La calculatrice TD2 (`Calculator`) est déployée comme composant EJB autonome et réutilisable. HISSAB l'injecte via `@EJB ICalculator` pour effectuer les calculs atomiques.

```
┌─────────────────────────────────────────────────────────────┐
│                     Navigateur élève                         │
│            index.html  +  hissab.js  (HTML/JS)              │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/REST (JSON)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   HISSAB.war (GlassFish 7)                   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Couche Web (JAX-RS)                                  │   │
│  │  HissabRestService  @Path("/hissab")                  │   │
│  │  ApplicationConfig  @ApplicationPath("/api")          │   │
│  │  OcrUtil            (appel OCR.space API)             │   │
│  └───────────┬──────────────────┬────────────────────────┘  │
│              │ @EJB @Local      │ @EJB @Local                │
│              ▼                  ▼                            │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │  HissabExpressionEJB │  │  HistoriqueEJB               │ │
│  │  @Stateless @Local   │  │  @Stateless @Local           │ │
│  │  InfixToPostfix +    │  │  persiste dans Derby (JPA)   │ │
│  │  @EJB ICalculator    │  │                              │ │
│  └──────────┬───────────┘  └────────────┬─────────────────┘ │
│             │ @EJB @Remote              │ EntityManager (JPA)│
└─────────────┼────────────────────────── ┼────────────────────┘
              │                           ▼
              │                ┌──────────────────────────────┐
              │                │  Derby (JavaDB)               │
              │                │  table : CALC_HISTORIQUE      │
              │                └──────────────────────────────┘
              │ JNDI (java:global/calc-ejb/Calculator)
              ▼
┌─────────────────────────────────────────────────────────────┐
│                  calc-ejb.jar (GlassFish 7)                  │
│                  déployé indépendamment                       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Calculator  @Stateless  @Remote                      │   │
│  │  ICalculator @Remote                                  │   │
│  │                                                       │   │
│  │  sum(a,b)      → Evaluator("a b +").interpret(ctx)    │   │
│  │  minus(a,b)    → Evaluator("a b -").interpret(ctx)    │   │
│  │  product(a,b)  → Evaluator("a b *").interpret(ctx)    │   │
│  │  divide(a,b)   → Evaluator("a b /").interpret(ctx)    │   │
│  │  factorial(n)  → boucle itérative                     │   │
│  │                                                       │   │
│  │  Pattern Interpreter TD2 (Expression, Plus, Minus,    │   │
│  │  Multiply, Divide, NumberLiteral, Variable, Evaluator)│   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Réutilisable par toute application sur le même GlassFish   │
└─────────────────────────────────────────────────────────────┘
```

### Flux de traitement

```
[Image/PDF ou texte saisi]
    → OCR (OCR.space)           → "5 + 2 × 6 − 3"
    → normalisation Unicode     → "5 + 2 * 6 - 3"
    → InfixToPostfixConverter   → "5 2 6 * + 3 -"  (Shunting-Yard)
    → évaluation token/token :
        calc.product(2, 6) = 12
        calc.sum(5, 12)    = 17
        calc.minus(17, 3)  = 14.0
    → Derby (JPA)               → historique enregistré
    → JSON                      → { expression, resultat, succes }
```

---

## 4. Structure du projet — Deux modules Maven

```
HISSAB/                                       ← parent Maven (packaging=pom)
├── pom.xml
│
├── calc-ejb/                                 ← MODULE 1 : composant CALC (EJB-JAR)
│   ├── pom.xml
│   └── src/main/java/
│       └── calculator/
│           ├── ICalculator.java              @Remote  (sum, minus, product, divide, factorial)
│           ├── Calculator.java               @Stateless implements ICalculator
│           └── interpreter/
│               ├── Expression.java           interface du pattern
│               ├── NumberLiteral.java        feuille numérique
│               ├── Variable.java             feuille variable
│               ├── Plus.java                 nœud addition
│               ├── Minus.java                nœud soustraction
│               ├── Multiply.java             nœud multiplication
│               ├── Divide.java               nœud division
│               └── Evaluator.java            construit l'arbre depuis postfixe
│
└── hissab-war/                               ← MODULE 2 : application web (WAR)
    ├── pom.xml
    └── src/main/
        ├── java/hissab/
        │   ├── ejb/
        │   │   ├── IHissabExpressionLocal.java   @Local interface EJB #2
        │   │   ├── HissabExpressionEJB.java       @Stateless EJB #2
        │   │   ├── InfixToPostfixConverter.java   Shunting-Yard (plain Java)
        │   │   ├── IHistoriqueLocal.java           @Local interface EJB #3
        │   │   ├── HistoriqueEJB.java              @Stateless EJB #3
        │   │   └── CalculHistorique.java           @Entity JPA
        │   └── web/
        │       ├── ApplicationConfig.java          JAX-RS config
        │       ├── HissabRestService.java          REST endpoints
        │       ├── OcrUtil.java                    OCR.space helper
        │       └── ResultatDTO.java                DTO réponse JSON
        ├── resources/
        │   └── META-INF/
        │       └── persistence.xml
        └── webapp/
            ├── index.html
            ├── hissab.js
            └── WEB-INF/
                ├── web.xml
                └── glassfish-resources.xml         config DataSource Derby auto
```

---

## 5. Composants détaillés

### 5.1 EJB #1 — `Calculator` (module calc-ejb)

**Interface `@Remote` :**
```java
package calculator;
import jakarta.ejb.Remote;

@Remote
public interface ICalculator {
    double sum(double a, double b);
    double minus(double a, double b);
    double product(double a, double b);
    double divide(double a, double b);
    long factorial(int n);
}
```

**Implémentation :**
- Annotation : `@Stateless`
- Chaque opération délègue au **pattern Interpreter TD2** (notation postfixe) :
  - `sum(a, b)` → `new Evaluator("a b +").interpret({a, b})`
  - `product(a, b)` → `new Evaluator("a b *").interpret({a, b})`
- `divide(a, 0)` retourne `Double.POSITIVE_INFINITY` (détecté en aval)
- `factorial(n < 0)` lance `IllegalArgumentException`

> **Pourquoi `@Remote` ?**  
> `@Remote` expose le composant à d'autres applications sur le même GlassFish. `HissabExpressionEJB` l'injecte avec `@EJB ICalculator calc` — GlassFish résout automatiquement le JNDI sans configuration manuelle.

---

### 5.2 EJB #2 — `HissabExpressionEJB` (module hissab-war)

**Interface `@Local` :**
```java
package hissab.ejb;
import jakarta.ejb.Local;

@Local
public interface IHissabExpressionLocal {
    double evaluerExpression(String expression) throws IllegalArgumentException;
}
```

**Implémentation :**
- Annotation : `@Stateless`
- Injecte : `@EJB ICalculator calc` (composant CALC distant)
- Pipeline d'évaluation :
  1. Normalisation Unicode → ASCII (`×→*`, `÷→/`, `−→-`)
  2. `InfixToPostfixConverter.convert()` → postfixe (Shunting-Yard)
  3. Évaluation token par token : pour chaque opérateur appelle `calc.sum/minus/product/divide()`
- Exemple : `"5 + 2 * 6 - 3"` → `"5 2 6 * + 3 -"` → `calc.product(2,6)=12` → `calc.sum(5,12)=17` → `calc.minus(17,3)=14.0`
- Vérifie `isNaN` et `isInfinite` après évaluation → lance `IllegalArgumentException`

---

### 5.3 EJB #3 — `HistoriqueEJB` (module hissab-war)

**Interface `@Local` :**
```java
package hissab.ejb;
import jakarta.ejb.Local;
import java.util.List;

@Local
public interface IHistoriqueLocal {
    void sauvegarder(String expression, double resultat);
    List<CalculHistorique> getTout();
}
```

**Implémentation :**
- Annotation : `@Stateless`
- Injecte : `@PersistenceContext EntityManager em`
- `sauvegarder()` : crée et persiste un `CalculHistorique` avec horodatage automatique
- `getTout()` : exécute `SELECT c FROM CalculHistorique c ORDER BY c.dateCalcul DESC`

---

### 5.4 Entité JPA — `CalculHistorique`

```java
@Entity
@Table(name = "CALC_HISTORIQUE")
public class CalculHistorique implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String expression;

    @Column(nullable = false)
    private double resultat;

    @Column(name = "DATE_CALCUL")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCalcul;

    @PrePersist
    public void preInsert() { this.dateCalcul = new Date(); }

    // getters / setters ...
}
```

---

### 5.5 DTO — `ResultatDTO`

Sépare la couche JPA de la couche REST. Évite d'exposer directement l'entité au client.

```java
public class ResultatDTO {
    public String expression;
    public double resultat;
    public String message;      // ex: "Calcul réussi" ou message d'erreur
    public boolean succes;
}
```

---

### 5.6 Service REST — `HissabRestService`

- `@Path("/hissab")`, `@RequestScoped`
- Injecte : `@EJB IHissabExpressionLocal expressionEJB` et `@EJB IHistoriqueLocal historique`

**Endpoint 1 — POST /api/hissab/calculer**
```
Content-Type: multipart/form-data
Paramètres :
  - fichier    (optionnel) : image PNG/JPG ou PDF
  - expression (optionnel) : texte saisi manuellement

Réponse succès (HTTP 200) :
  { "expression": "5 + 2 * 6 - 3", "resultat": 14.0, "succes": true, "message": "Calcul réussi" }

Réponse erreur expression invalide (HTTP 400) :
  { "succes": false, "message": "Expression invalide : Token inconnu : @" }

Réponse erreur OCR sans fallback (HTTP 400) :
  { "succes": false, "message": "OCR échoué — ... Saisissez l'expression manuellement." }
```

Logique :
1. Si `fichier` fourni → `OcrUtil.extraireTexte()` → expression texte (ou erreur OCR)
2. Si OCR vide/échoué ET `expression` fourni → utiliser la saisie manuelle
3. Si rien du tout → retourner HTTP 400
4. Appeler `expressionEJB.evaluerExpression()` — attraper `EJBException`/`IllegalArgumentException` → HTTP 400
5. Appeler `historique.sauvegarder(expression, resultat)`
6. Retourner `ResultatDTO` avec HTTP 200

**Endpoint 2 — GET /api/hissab/historique**
```
Réponse (HTTP 200) :
  [ { "id":1, "expression":"5+2*6-3", "resultat":14.0, "dateCalcul":"2026-05-12T14:30:00" }, ... ]
```

---

### 5.7 OCR — `OcrUtil`

- Encode l'image en Base64
- Envoie POST à `https://api.ocr.space/parse/image` avec `java.net.http.HttpClient`
- Parse le JSON retourné (champ `ParsedText`)
- Nettoie le texte : `×` → `*`, `÷` → `/`, `−` → `-`, supprime espaces multiples
- Lance `RuntimeException` avec message descriptif si erreur réseau ou texte vide

---

### 5.8 `ApplicationConfig.java`

```java
package hissab.web;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api")
public class ApplicationConfig extends Application {}
```

Tous les endpoints REST sont accessibles sous `/api/hissab/...`.

---

## 6. Configuration — `persistence.xml`

Fichier : `hissab-war/src/main/resources/META-INF/persistence.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
  <persistence-unit name="hissabPU" transaction-type="JTA">
    <jta-data-source>jdbc/hissabDS</jta-data-source>
    <class>hissab.ejb.CalculHistorique</class>
    <properties>
      <!-- EclipseLink (provider JPA de GlassFish 7) — PAS Hibernate -->
      <property name="eclipselink.ddl-generation" value="create-tables"/>
      <property name="eclipselink.ddl-generation.output-mode" value="database"/>
      <property name="eclipselink.logging.level" value="WARNING"/>
    </properties>
  </persistence-unit>
</persistence>
```

> `create-tables` crée la table si elle n'existe pas, sans effacer les données existantes.

---

## 7. Configuration — `glassfish-resources.xml`

Fichier : `hissab-war/src/main/webapp/WEB-INF/glassfish-resources.xml`

Crée le DataSource Derby **automatiquement** au déploiement — pas besoin de passer par la console admin.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE resources PUBLIC
  "-//GlassFish.org//DTD GlassFish Application Server 3.1 Resource Definitions//EN"
  "http://glassfish.org/dtds/glassfish-resources_1_5.dtd">
<resources>
  <jdbc-connection-pool
      name="HissabDerbyPool"
      res-type="javax.sql.DataSource"
      datasource-classname="org.apache.derby.jdbc.EmbeddedDataSource">
    <property name="DatabaseName" value="${com.sun.aas.instanceRoot}/databases/hissabDB"/>
    <property name="CreateDatabase" value="create"/>
  </jdbc-connection-pool>

  <jdbc-resource
      pool-name="HissabDerbyPool"
      jndi-name="jdbc/hissabDS"/>
</resources>
```

> Derby **embarqué** — pas besoin de démarrer un serveur Derby séparé.

---

## 8. Interface élève — `index.html` + `hissab.js`

**Sections de la page :**
1. **Zone de saisie** — champ texte pour l'expression + upload image/PDF + bouton "Vérifier"
2. **Zone résultat** — affiche expression détectée + résultat + message (succès ou erreur)
3. **Tableau historique** — liste des calculs précédents, chargée au démarrage et après chaque calcul

**Flux JS :**
```javascript
// Envoi du calcul
const formData = new FormData();
if (fichier) formData.append('fichier', fichier);
if (expression) formData.append('expression', expression);

const response = await fetch('/HISSAB/api/hissab/calculer', {
    method: 'POST', body: formData
});
const data = await response.json();
// afficher data.expression, data.resultat, data.message

// Chargement historique
const hist = await fetch('/HISSAB/api/hissab/historique').then(r => r.json());
```

---

## 9. Dépendances Maven

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
    <!-- Interfaces ICalculator uniquement — EJB déployé séparément -->
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

> `classifier=client` : `maven-ejb-plugin` génère automatiquement `calc-ejb-client.jar` contenant seulement les interfaces. `hissab-war` compile contre ce JAR (scope=provided) sans embarquer le code EJB dans le WAR.

---

## Lancer le projet

### 1. Prérequis

- Java 17+ — `java -version`
- Maven 3.8+ — `mvn -version`
- GlassFish 7.0.x installé (ex : `C:\glassfish7\`)
- Ajouter `C:\glassfish7\bin` au PATH

### 2. Démarrer GlassFish

```cmd
asadmin start-domain
```

Application : `http://localhost:8080` — Console admin : `http://localhost:4848`

### 3. Compiler

```cmd
cd C:\Users\MICROSOFT PRO\ALOG\ZIADI_BOUKAKIOU_SIQ1_LAB4\HISSAB
mvn clean package -DskipTests
```

Artefacts générés :
```
calc-ejb\target\calc-ejb-1.0.jar             ← composant CALC (EJB-JAR)
calc-ejb\target\calc-ejb-1.0-client.jar      ← interfaces uniquement
hissab-war\target\HISSAB.war                 ← application web
```

### 4. Déployer (ordre obligatoire)

> `calc-ejb` **doit** être déployé avant `hissab-war` — HISSAB injecte `@EJB ICalculator` via JNDI.

```cmd
asadmin deploy calc-ejb\target\calc-ejb-1.0.jar
asadmin deploy hissab-war\target\HISSAB.war
```

Vérifier :
```cmd
asadmin list-applications
```
Résultat attendu :
```
calc-ejb    <ejb>
hissab-war  <web>
```

### 5. Accéder à l'application

**`http://localhost:8080/HISSAB/`**

### 6. Tester l'API

```cmd
curl -X POST http://localhost:8080/HISSAB/api/hissab/calculer ^
  -F "expression=5 + 2 * 6 - 3"
```
Réponse attendue :
```json
{"expression":"5 + 2 * 6 - 3","resultat":14.0,"succes":true,"message":"Calcul réussi"}
```

```cmd
curl http://localhost:8080/HISSAB/api/hissab/historique
```

### 7. Redéployer après modification

```cmd
mvn clean package -DskipTests
asadmin undeploy hissab-war
asadmin undeploy calc-ejb
asadmin deploy calc-ejb\target\calc-ejb-1.0.jar
asadmin deploy hissab-war\target\HISSAB.war
```

### Commandes utiles

| Commande | Action |
|---|---|
| `asadmin start-domain` | Démarrer GlassFish |
| `asadmin stop-domain` | Arrêter GlassFish |
| `asadmin list-applications` | Lister les applications déployées |
| `asadmin deploy calc-ejb\target\calc-ejb-1.0.jar` | Déployer CALC |
| `asadmin deploy hissab-war\target\HISSAB.war` | Déployer HISSAB |
| `asadmin undeploy calc-ejb` | Désinstaller CALC |
| `asadmin undeploy hissab-war` | Désinstaller HISSAB |
| `mvn clean package -DskipTests` | Compiler et packager |

---

## 10. Plan de réalisation

| # | Étape | Durée |
|---|-------|-------|
| 1 | Créer structure multi-module Maven (parent + calc-ejb + hissab-war) | 20 min |
| 2 | Écrire `ICalculator @Remote` + `Calculator @Stateless` avec pattern Interpreter TD2 | 30 min |
| 3 | Tester : `Calculator.sum(5,3)=8`, `product(2,6)=12`, `factorial(5)=120` | 15 min |
| 4 | Déployer `calc-ejb.jar` sur GlassFish | 10 min |
| 5 | Écrire `InfixToPostfixConverter` (Shunting-Yard) dans `hissab-war` | 20 min |
| 6 | Écrire `IHissabExpressionLocal` + `HissabExpressionEJB` | 25 min |
| 7 | Écrire `CalculHistorique` (entité JPA) | 15 min |
| 8 | Écrire `persistence.xml` + `glassfish-resources.xml` | 20 min |
| 9 | Écrire `IHistoriqueLocal` + `HistoriqueEJB` | 20 min |
| 10 | Déployer `HISSAB.war` — vérifier injection `@EJB ICalculator` + table Derby | 15 min |
| 11 | Écrire `ApplicationConfig` + `ResultatDTO` | 10 min |
| 12 | Écrire `OcrUtil` (appel OCR.space API) | 25 min |
| 13 | Écrire `HissabRestService` (2 endpoints REST) | 30 min |
| 14 | Créer `index.html` + `hissab.js` (interface élève) | 40 min |
| 15 | Tests complets (OCR, calcul, historique, erreurs) | 30 min |
| 16 | Préparation ZIP + enregistrement vidéo démo (3 min) | 20 min |

**Total estimé : ~6h05**

---

## 11. Points d'attention — Tableau de bord

### 11.1 Imports Jakarta EE 10 — CRITIQUE

GlassFish 7 = Jakarta EE 10 → **tous les imports sont `jakarta.*`**, jamais `javax.*`.

| ❌ Ancien (exemples prof) | ✅ HISSAB |
|---|---|
| `javax.ejb.Stateless` | `jakarta.ejb.Stateless` |
| `javax.ejb.Remote` | `jakarta.ejb.Remote` |
| `javax.ejb.Local` | `jakarta.ejb.Local` |
| `javax.ejb.EJB` | `jakarta.ejb.EJB` |
| `javax.ws.rs.Path` | `jakarta.ws.rs.Path` |
| `javax.persistence.*` | `jakarta.persistence.*` |

### 11.2 Évaluation des expressions — Pattern Interpreter + Shunting-Yard

Le composant `Calculator` (calc-ejb) utilise le **pattern Interpreter TD2** — les opérations atomiques sont évaluées en notation postfixe. `HissabExpressionEJB` (hissab-war) convertit l'expression infixe en postfixe via Shunting-Yard (`InfixToPostfixConverter`), puis appelle `ICalculator` pour chaque opération :

```
"5 + 2 * 6 - 3"
   → InfixToPostfixConverter → "5 2 6 * + 3 -"
   → token "*" : calc.product(2, 6) = 12
   → token "+" : calc.sum(5, 12)   = 17
   → token "-" : calc.minus(17, 3) = 14.0
```

### 11.3 Ordre de déploiement — CRITIQUE

`calc-ejb.jar` doit être déployé **avant** `HISSAB.war`. `HissabExpressionEJB` injecte `@EJB ICalculator` — si le composant CALC n'est pas dans le registre JNDI au démarrage du WAR, l'injection échoue et GlassFish retourne `NameNotFoundException`.

### 11.4 Provider JPA — EclipseLink

GlassFish 7 utilise **EclipseLink**, pas Hibernate. Ne jamais utiliser les propriétés `hibernate.*` dans `persistence.xml`.

### 11.5 DataSource Derby embarqué

`glassfish-resources.xml` configure Derby en mode **embarqué** (fichiers dans le répertoire GlassFish). Pas besoin de démarrer un serveur Derby séparé ni de passer par la console admin.

### 11.6 OCR.space API

La clé est intégrée dans `OcrUtil.java` — aucune configuration nécessaire. Clé de secours disponible sur `https://ocr.space/ocrapi`. La clé de test `helloworld` est limitée à 3 req/10s.

### 11.7 Schéma de la base de données

EclipseLink crée la table automatiquement grâce à `create-tables`. En cas de besoin manuel :
```sql
CREATE TABLE CALC_HISTORIQUE (
    ID          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    EXPRESSION  VARCHAR(500) NOT NULL,
    RESULTAT    DOUBLE       NOT NULL,
    DATE_CALCUL TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

---

# TP4-ALOG
