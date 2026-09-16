# Tropimon Casino

Mod client Fabric 1.21.1 pour Cobblemon 1.7.2. Ouvrir l'interface avec `ù`
ou la commande client `/casino`.

## Commandes de la machine

- Bouton gauche : changer la mise.
- Bouton central : lancer les trois rouleaux, puis arrêter successivement le
  rouleau 1, le rouleau 2 et le rouleau 3. Aucun rouleau ne s'arrête tout seul.
- Bouton droit : ouvrir le menu du solde Poké$, de l'historique et de
  l'administration.

La façade dorée passe derrière le cadre bleu/cyan Tropimon : ses bords sont rognés
par le cadre et aucun fond noir ne reste visible. Les écrans secondaires utilisent
la zone centrale afin de garder la navigation lisible. Seuls les chiffres des
voyants latéraux ne clignotent pas en boucle : seul le numéro du prochain rouleau
à arrêter reste éclairé pendant une partie. Toute l'interface utilise une
police uniforme lisible et suit automatiquement la langue française ou anglaise
sélectionnée par le joueur.

Psykokwak, Ramoloss, Miaouss et Ectoplasma sont rendus avec leurs modèles 3D et
leurs textures natives Cobblemon, comme dans le monde. Le mod ne recopie pas ces
ressources et reste indépendant des autres mods Tropimon.

La table des gains intégrée explique les multiplicateurs. Le RTP affiché tient
compte de la mise courante et du plafond de gain configuré. Le tirage, les soldes,
les rôles et les opérations sont validés par le backend
Supabase du projet. Le client ne calcule jamais le résultat gagnant. La table de
gain actuelle a un RTP théorique de 93,1046 %, soit un avantage maison de 6,8954 %.

## Économie en Poké$

- Le joueur dépose des Poké$ avec `/pay` vers le compte banque configuré.
- Un admin vérifie le paiement puis crédite le même montant sur le solde casino.
- Les mises et les gains sont directement exprimés en Poké$, sans jetons ni conversion.
- Un retrait réserve immédiatement les Poké$ du solde casino.
- Le paiement est préparé par `/pay`, puis marqué payé séparément.
- Les retraits doivent être traités sous 24 à 72 heures maximum.
- Le super-admin est la banque : solde initial nul, transactions joueur interdites
  et partie de démonstration illimitée sans modifier son solde.

Le premier démarrage authentifié consomme le secret local d'activation, attribue
le rôle super-admin au joueur courant et configure automatiquement son pseudo comme
compte banque. Ce secret n'est jamais inclus dans le dépôt ni dans les JAR.

## Administration

Les admins peuvent valider/refuser les opérations des autres joueurs et suivre les
retards dès 24 heures et leur échéance à 72 heures. Le journal administratif est
consultable dans l'interface. Seul le super-admin nomme ou retire des admins et
modifie les mises, le gain maximal, le compte banque et l'ouverture du casino.
Toutes ces actions sont journalisées. Une auto-validation ou un auto-paiement est
refusé côté serveur.

## Limite du mode client seul

L'identité est liée au UUID Minecraft déclaré par le client et rendue unique dans la
base. Sans plugin serveur d'authentification, un client volontairement modifié reste
capable de mentir sur ce UUID avant sa première liaison. Les soldes, tirages, rôles
et validations restent malgré tout calculés et protégés côté Supabase.

Développement : **By FastedCorsi**.


## Mises à jour automatiques

Le mod vérifie sa propre Release GitHub au démarrage, au maximum une fois toutes les six heures. Lorsqu'une version plus récente est disponible, son JAR et son SHA-256 sont contrôlés, puis la mise à jour est installée après l'arrêt de Minecraft avec sauvegarde de l'ancien JAR. Le launcher peut rester ouvert.

La vérification s'effectue en arrière-plan et n'ajoute aucun travail par tick. Elle peut être désactivée avec "enabled": false dans config/tropimon_casino-updater.json.

