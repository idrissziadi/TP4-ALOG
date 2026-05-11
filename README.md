# HISSAB — Document de Conception (Spec)

**Date :** 2026-05-08  
**Auteurs :** Ziadi, Boukakiou  
**Module :** SIQ1 — Architecture Logicielle (LAB4)  
**Version :** 2.0 (corrigée après review approfondie)

---

## 1. Objectif du projet

HISSAB est un système web qui aide des élèves de primaire à vérifier des calculs mathématiques. L'élève soumet une image ou un PDF contenant une expression (ex : `5 + 2 × 6 − 3`), le système extrait l'expression, la calcule automatiquement et retourne le résultat. Chaque calcul est enregistré dans un historique persistant.

---

## 2. Contraintes du TP

- Minimum **2 EJBs** (Enterprise JavaBeans)
- Architecture **à base de composants** (JEE / GlassFish 7.0.25)
- Client : **HTML + JavaScript** avec API REST (JAX-RS)
- Base de données : **Derby** (intégrée à GlassFish)
- Réutiliser la logique du calculateur du TD précédent via `exp4j`
- Livrables : ZIP du code source + vidéo de démo (max 3 min)

---

## 3. Architecture — Vue d'ensemble

**Modèle de déploiement : WAR unique** (un seul fichier `.war` déployé sur GlassFish).

En JEE 6+, les EJBs peuvent être embarqués directement dans un WAR — c'est l'approche la plus simple, elle évite la configuration EAR et l'injection `@EJB` fonctionne nativement car tout est dans la même JVM.

```
┌─────────────────────────────────────────────────────────────┐
│                     Navigateur élève                         │
│            index.html  +  hissab.js  (HTML/JS)              │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/REST (JSON)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  HISSAB.war (GlassFish 7)                    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Couche Web (JAX-RS)                                  │   │
│  │  HissabRestService  @Path("/hissab")                  │   │
│  │  ApplicationConfig  @ApplicationPath("/api")          │   │
│  │  OcrUtil            (appel OCR.space API)             │   │
│  └───────────┬──────────────────┬────────────────────────┘  │
│              │ @EJB @Local      │ @EJB @Local                │
│              ▼                  ▼                            │
│  ┌─────────────────┐  ┌──────────────────────────────────┐  │
│  │  CalcEJB        │  │  HistoriqueEJB                   │  │
│  │  @Stateless     │  │  @Stateless                      │  │
│  │  @Local         │  │  @Local                          │  │
│  │  évalue expr.   │  │  persiste dans Derby (JPA)       │  │
│  │  via exp4j      │  │                                  │  │
│  └─────────────────┘  └──────────────┬───────────────────┘  │
│                                      │ EntityManager (JPA)   │
│                                      ▼                       │
│                        ┌─────────────────────────────────┐  │
│                        │  Derby (JavaDB)                  │  │
│                        │  table : CALC_HISTORIQUE         │  │
│                        └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Structure du projet — Un seul projet Maven WAR

```
HISSAB/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/hissab/
    │   │   ├── ejb/
    │   │   │   ├── ICalcLocal.java          @Local interface
    │   │   │   ├── CalcEJB.java             @Stateless EJB #1
    │   │   │   ├── IHistoriqueLocal.java    @Local interface
    │   │   │   ├── HistoriqueEJB.java       @Stateless EJB #2
    │   │   │   └── CalculHistorique.java    @Entity JPA
    │   │   └── web/
    │   │       ├── ApplicationConfig.java   JAX-RS config
    │   │       ├── HissabRestService.java   REST endpoints
    │   │       ├── OcrUtil.java             OCR.space helper
    │   │       └── ResultatDTO.java         DTO réponse JSON
    │   ├── resources/
    │   │   └── META-INF/
    │   │       └── persistence.xml
    │   └── webapp/
    │       ├── index.html
    │       ├── hissab.js
    │       └── WEB-INF/
    │           ├── web.xml
    │           └── glassfish-resources.xml  ← config DataSource auto
    └── test/
        └── java/hissab/
            └── CalcEJBTest.java
```

---

## 5. Composants détaillés

### 5.1 EJB #1 — `CalcEJB` (Calcul)

**Interface :**
```java
package hissab.ejb;
import jakarta.ejb.Local;

@Local
public interface ICalcLocal {
    double evaluerExpression(String expression) throws IllegalArgumentException;
}
```

**Implémentation :**
- Annotation : `@Stateless`
- Dépendance : bibliothèque `exp4j` (évalue expressions infixes avec priorité des opérateurs)
- Exemple : `"5 + 2 * 6 - 3"` → `34.0`
- Lance `IllegalArgumentException` si l'expression est invalide (division par zéro, syntaxe incorrecte)

> **Pourquoi exp4j et pas l'Evaluator du TD ?**  
> L'Evaluator du TD utilise la notation **postfixe** (`"a b +"`). OCR extrait des expressions **infixes** (`"5 + 2 * 6 - 3"`). Ces formats sont incompatibles. `exp4j` gère nativement les expressions infixes avec la bonne priorité des opérateurs, sans réécrire un parser.

---

### 5.2 EJB #2 — `HistoriqueEJB` (Persistance)

**Interface :**
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

### 5.3 Entité JPA — `CalculHistorique`

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

### 5.4 DTO — `ResultatDTO`

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

### 5.5 Service REST — `HissabRestService`

- `@Path("/hissab")`, `@RequestScoped`
- Injecte : `@EJB ICalcLocal calc` et `@EJB IHistoriqueLocal historique`

**Endpoint 1 — POST /api/hissab/calculer**
```
Content-Type: multipart/form-data
Paramètres :
  - fichier    (optionnel) : image PNG/JPG ou PDF
  - expression (optionnel) : texte saisi manuellement

Réponse succès (HTTP 200) :
  { "expression": "5 + 2 * 6 - 3", "resultat": 34.0, "succes": true, "message": "Calcul réussi" }

Réponse erreur expression invalide (HTTP 400) :
  { "expression": "5 ++ 3", "resultat": 0, "succes": false, "message": "Expression invalide" }

Réponse erreur OCR sans fallback (HTTP 400) :
  { "succes": false, "message": "Extraction impossible, veuillez saisir l'expression manuellement" }
```

Logique :
1. Si `fichier` fourni → `OcrUtil.extraireTexte()` → expression texte (ou `null` si échec OCR)
2. Si `expression` fourni directement OU si OCR a retourné du texte → utiliser l'expression
3. Si les deux sont absents/vides → retourner HTTP 400
4. Appeler `calc.evaluerExpression(expression)` — attraper `IllegalArgumentException` → HTTP 400
5. Appeler `historique.sauvegarder(expression, resultat)`
6. Retourner `ResultatDTO` avec HTTP 200

**Endpoint 2 — GET /api/hissab/historique**
```
Réponse (HTTP 200) :
  [ { "id":1, "expression":"5+2*6-3", "resultat":34.0, "dateCalcul":"2026-05-08T14:30:00" }, ... ]
```

---

### 5.6 OCR — `OcrUtil`

- Encode l'image en Base64
- Envoie POST à `https://api.ocr.space/parse/image` avec `java.net.http.HttpClient`
- Parse le JSON retourné (champ `ParsedText`)
- Nettoie le texte : `×` → `*`, `÷` → `/`, `−` → `-`, supprime espaces multiples
- Retourne `null` si erreur réseau ou texte vide

---

### 5.7 `ApplicationConfig.java`

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

> **Important :** `create-tables` crée la table si elle n'existe pas, sans effacer les données existantes. Idéal pour le TP.

---

## 7. Configuration — `glassfish-resources.xml`

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

> Derby **embarqué** (pas réseau) : plus simple, pas besoin de démarrer un serveur Derby séparé.

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

## 9. Dépendances Maven — `pom.xml`

```xml
<project>
  <groupId>hissab</groupId>
  <artifactId>HISSAB</artifactId>
  <version>1.0</version>
  <packaging>war</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
  </properties>

  <dependencies>
    <!-- Jakarta EE 10 — fourni par GlassFish, ne pas inclure dans le WAR -->
    <dependency>
      <groupId>jakarta.platform</groupId>
      <artifactId>jakarta.jakartaee-api</artifactId>
      <version>10.0.0</version>
      <scope>provided</scope>
    </dependency>

    <!-- exp4j : évaluation d'expressions mathématiques infixes -->
    <dependency>
      <groupId>net.objecthunter</groupId>
      <artifactId>exp4j</artifactId>
      <version>0.4.8</version>
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

---

## 10. Plan de réalisation — Étapes corrigées

| # | Étape | Durée |
|---|-------|-------|
| 1 | Créer projet Maven `HISSAB` (WAR), configurer `pom.xml` avec `exp4j` | 20 min |
| 2 | Écrire `ICalcLocal` + `CalcEJB` avec `exp4j` — tester `"5+2*6-3"` = 34 | 25 min |
| 3 | Écrire `CalculHistorique` (entité JPA) | 15 min |
| 4 | Écrire `persistence.xml` avec propriétés EclipseLink | 15 min |
| 5 | Écrire `glassfish-resources.xml` pour DataSource Derby auto | 10 min |
| 6 | Écrire `IHistoriqueLocal` + `HistoriqueEJB` | 20 min |
| 7 | Déployer le WAR sur GlassFish — vérifier création table Derby | 15 min |
| 8 | Écrire `ApplicationConfig` + `ResultatDTO` | 10 min |
| 9 | Écrire `OcrUtil` (appel OCR.space API) | 25 min |
| 10 | Écrire `HissabRestService` (2 endpoints REST) | 30 min |
| 11 | Créer `index.html` + `hissab.js` (interface élève) | 40 min |
| 12 | Tests complets (OCR, calcul, historique, erreurs) | 30 min |
| 13 | Préparation ZIP + enregistrement vidéo démo (3 min) | 20 min |

**Total estimé : ~5h15**

---

## 11. Points d'attention — Tableau de bord

### 11.1 Imports Jakarta EE 10 — CRITIQUE

GlassFish 7 = Jakarta EE 10 → **tous les imports sont `jakarta.*`**, jamais `javax.*`.

| ❌ Ancien (exemples prof) | ✅ Nouveau (HISSAB) |
|---|---|
| `javax.ejb.Stateless` | `jakarta.ejb.Stateless` |
| `javax.ejb.Local` | `jakarta.ejb.Local` |
| `javax.ejb.EJB` | `jakarta.ejb.EJB` |
| `javax.ws.rs.Path` | `jakarta.ws.rs.Path` |
| `javax.persistence.*` | `jakarta.persistence.*` |

### 11.2 Évaluation des expressions — exp4j

`exp4j` est le choix retenu. L'`Evaluator` du TD est postfixe uniquement — incompatible avec les expressions extraites par OCR (format infixe). Ne pas essayer de réutiliser l'Evaluator pour cette partie.

### 11.3 Provider JPA — EclipseLink

GlassFish 7 utilise **EclipseLink**, pas Hibernate. Ne jamais utiliser les propriétés `hibernate.*` dans `persistence.xml`.

### 11.4 DataSource Derby embarqué

Le `glassfish-resources.xml` configure Derby en mode **embarqué** (fichiers dans le répertoire GlassFish). Pas besoin de démarrer un serveur Derby séparé ni de passer par la console admin.

### 11.5 OCR.space API

Clé gratuite sur https://ocr.space/ocrapi. La clé de test `helloworld` est limitée à 3 req/10s. Créer un compte gratuit pour obtenir sa propre clé avant la démo vidéo.

### 11.6 Schéma de la base de données

EclipseLink créera la table automatiquement grâce à `create-tables`. En cas de besoin manuel :
```sql
CREATE TABLE CALC_HISTORIQUE (
    ID          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    EXPRESSION  VARCHAR(500) NOT NULL,
    RESULTAT    DOUBLE       NOT NULL,
    DATE_CALCUL TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```
# TP4-ALOG
