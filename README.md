# Reconnaisseur de langage régulier (Java + JavaFX)

Regex ⇄ AFN ⇄ AFD ⇄ AFD minimal · lemme d'Arden · éditeur d'automates · chemin d'un mot (façon JFLAP).

## Lancer
Prérequis : JDK 17+ et Maven.

    mvn javafx:run

Vérification du moteur (sans interface, ~5 millions de contrôles) :

    mvn compile
    java -cp target/classes automates.engine.SelfTest        # graine par défaut
    java -cp target/classes automates.engine.SelfTest 2026   # autre graine aléatoire

## Étape 1 : définir l'alphabet Σ (barre jaune en haut)
Σ doit être défini **avant** de saisir une regex ou de dessiner un automate ; tant qu'il ne l'est pas, les deux onglets sont verrouillés.
- Saisie libre (`a,b`, `{0, 1}`, `a b c`) ou raccourcis {a,b} {0,1} {a,b,c}… ; seuls lettres et chiffres sont permis (ε, λ, ! désignent le mot vide, pas un symbole).
- Regex : un symbole absent de Σ est refusé avec sa position (« le symbole c n'appartient pas à Σ = {a, b} »).
- Éditeur : une transition avec un symbole hors Σ est refusée ; un automate chargé qui en contient est signalé.
- Mots testés : une lettre hors Σ donne « MOT INVALIDE » (ce n'est pas un mot de Σ*), distinct de « REJETÉ ».
- Complétion / minimisation : le puits est calculé par rapport à Σ (et non aux seules lettres utilisées). Ex. : `a*` avec Σ = {a,b} → AFD minimal complet à 2 états (avec puits pour b).
- Changer Σ met à jour exemples, mots d'essai et résultats ; les expressions devenues incorrectes sont signalées.

## Présentation « comme au cours »
Les calculs sont rédigés comme dans les corrigés de TD :
- **Déterminisation** : états de l'AFD nommés A, B, C… ; lignes `ε-fer(trans(B, a)) = ε-fer(q0, q2) = {q0, q2} = C`, puis la **table de transition**, puis le **dessin de l'AFD**.
- **Minimisation** : « L'AFD est complet » (sinon ajout d'un puits p), puis `Π0 = {{finals}, {non finals}}`, `Π1`, `Π2`… avec, pour chaque classe, l'état d'arrivée de chaque lettre et la raison de la séparation ; arrêt quand `Π(k+1) = Πk` ; états de l'AFD minimal (`{B, E} → état B`) puis dessin.
- **Lemme d'Arden** : équations numérotées `q0 = a q1  (1)`, puis « (3) dans (2) : q1 = b·ε ⟹ q1 = b (4) », `X = AX + B ⟹ X = A*B`, résultat `L = q0 = aa*b`.

## Onglet « Regex → automates »
- **Écriture guidée** : vérification en direct (✔ / ✘ avec la position exacte de l'erreur et un « ^ » sous le caractère fautif), boutons d'insertion ε ∅ + | * ? ( ), exemples, et un panneau d'aide « Comment écrire une expression régulière ? ».
- **Deux notations** (boutons radio) :
  - *Cours / JFLAP* (par défaut) : union `+` (ou `|`), étoile `*`, ε (ou λ, !), ∅, concaténation implicite. `a+` est refusé (« + » est l'union) ; pour « un ou plus » écrire `aa*`.
  - *Étendue* : union `|`, `+` = « un ou plus ».
  - Tout autre caractère (`. [ ] { } ^ $ \`…), opérateur sans opérande (`*a`, `a+`, `()`), parenthèse non appariée → erreur expliquée.
- Regex → AFN-ε (Thompson) → AFD (sous-ensembles, avec le détail ligne par ligne) → **AFD minimal en étapes**.
- **Minimisation détaillée** (partitions successives de Moore) :
  - étape 0 : états inaccessibles supprimés ; si l'AFD est **incomplet**, ajout d'un **état puits p** (explication + dessin de l'AFD complet) ;
  - une étape par partition Π0, Π1, … : pour chaque classe, l'état d'arrivée de chaque lettre et la raison de la séparation, arrêt quand Π(k+1) = Πk ;
  - classes d'équivalence finales, **dessin de l'AFD minimal complet** (avec p) **et** de l'AFD minimal simplifié sans puits (incomplet, convention JFLAP).
  - le puits est créé par rapport à Σ défini en haut (ex. Σ = {a,b} et regex `a*` → puits pour b).
- **Test de mots** sur l'AFN, l'AFD, l'AFD minimal complet ou l'AFD minimal simplifié : pour chaque mot, pastille **ACCEPTÉ ✔ / REJETÉ ✘**, raison (blocage à telle lettre, ou état atteint non final…), trace de lecture, chemin acceptant (avec les ε), et bouton **« Voir le chemin ▶ »**.
- Équivalence de deux regex (renvoie un mot qui les distingue).

## Fenêtre « Chemin du mot » (comme JFLAP)
Ruban du mot (lettres lues / lettre courante), automate dessiné avec les **états courants en orange**, les **transitions empruntées en orange épais**, le parcours déjà fait en bleu ; boutons Début / Précédent / Suivant / Fin / Lecture auto ; journal « d'où on part, où on arrive » ; à la fin le verdict (vert = ACCEPTÉ, rouge = REJETÉ) et le chemin acceptant.

## Onglet « Éditeur → regex »
- Outils : État, Transition, Déplacer, Initial, Final, Supprimer ; transitions multiples `a,b`, boucles, ε (champ vide, ε, λ ou !). Symboles invalides refusés.
- **→ Regex (lemme d'Arden)** : système d'équations Xi, rappel du lemme (X = AX + B ⟹ X = A*B), puis chaque élimination en deux temps (Arden sur l'équation, substitution dans les autres), jusqu'à X(initial) ; résultat vérifié automatiquement par équivalence. Fonctionne pour AFD, AFN et AFN-ε ; états inutiles signalés.
- **→ AFD** (avec le détail des sous-ensembles) et **→ AFD minimal (étapes)** (même panneau que l'onglet 1, sur votre automate).
- Test de mots avec chemin, enregistrement / ouverture de fichiers `.auto`.

## Onglet « Intersection / union » (L₁ ∩ L₂ et L₁ ∪ L₂)
1. Donnez **deux langages** : chaque automate peut venir d'une **expression régulière**, de l'**automate dessiné dans l'éditeur**
   (bouton « Prendre l'automate… »), d'un **fichier .auto**, ou de l'**exemple du cours** (nombre pair de a / nombre pair de b).
2. Choisissez **∩** ou **∪** : tout s'affiche aussitôt, avec les explications :
   - Étapes 1 et 2 : chaque automate en **AFD complet** (déterminisation A, B, C… / A', B', C'… si c'est un AFN,
     puis état **puits p** si l'AFD est incomplet, avec la liste des transitions manquantes) ;
   - Étape 3 : **automate produit** : couples (P, X), ligne par ligne `a : q0 → q1 dans A1, q0' → q0' dans A2 ⟹ (q1,q0')`,
     seuls les couples atteignables sont construits ; états finals expliqués (∩ : P final **ET** X final ; ∪ : **OU**) ;
     table de transition et dessin ;
   - Étape 4 : **minimisation** (Π0, Π1, … comme ailleurs) et dessin de l'AFD minimal ;
   - Étape 5 : mots d'essai (dans L₁ ? dans L₂ ? dans le résultat ?), **vérification automatique**, test de mots
     avec ACCEPTÉ/REJETÉ et « Voir le chemin », et bouton **→ Expression régulière (lemme d'Arden)**.
3. Cas particuliers gérés : langage vide (∩ sans état final), automates incomplets (puits), symboles hors Σ (refusés).

## Fiabilité (ce que vérifie SelfTest)
Chaque théorème est recoupé avec une méthode indépendante :

| Théorème / algorithme | Vérifié contre |
|---|---|
| Kleene : regex → AFN (Thompson) | `java.util.regex` sur tous les mots jusqu'à longueur 6-7 |
| AFN → AFD (sous-ensembles) | simulation brute par parcours de configurations |
| Minimisation (Moore) | `java.util.regex` + **Brzozowski** (double renversement) : même nombre d'états |
| Complétion / puits | l'AFD minimal complet est bien complet ; complet = partiel + puits |
| Lemme d'Arden | l'expression obtenue est relue, comparée par équivalence **et** mot à mot |
| Élimination d'états (2ᵉ méthode) | idem, et recoupée avec Arden |
| Équivalence de deux automates | produit des AFD, mot distinguant vérifié |
| Trace / chemin acceptant | chemin rejoué transition par transition |

Jeux : 20 regex fixes, 400 regex aléatoires, 300 automates aléatoires avec ε, 18 syntaxes erronées,
20 paires fixes + 300 paires de regex + 200 paires d'automates pour ∩ et ∪ (contre java.util.regex et une simulation brute).

## Structure
    automates.model.Automaton        automate (états, transitions, initial, finals, noms)
    automates.engine.Re              arbre d'expression régulière + simplifications + affichage
    automates.engine.RegexParser     regex -> AFN (Thompson), deux syntaxes, erreurs avec position
    automates.engine.AutomatonOps    simulation, déterminisation (avec journal), complétion, équivalence
    automates.engine.Minimizer       minimisation de Moore avec le détail de chaque étape
    automates.engine.ArdenSolver     automate -> regex par le lemme d'Arden, étapes détaillées
    automates.engine.RegexBuilder    automate -> regex par élimination d'états (contre-vérification)
    automates.engine.ProductOps      intersection / union par automate produit (AFD complets, couples, finals)
    automates.engine.Simulator       lecture d'un mot : étapes, blocage, chemin acceptant
    automates.engine.SelfTest        auto-test du moteur
    automates.io.AutomatonIO         sauvegarde / chargement
    automates.ui.AlphabetModel/Bar   alphabet Σ commun (saisie, validation, verrouillage des onglets)
    automates.ui.OperationsTab       onglet « Intersection / union » (entrées, étapes, conclusion)
    automates.ui.*                   interface JavaFX
