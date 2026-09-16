# Règles permanentes — Tropimon Casino

- Attribution publique exacte : **By FastedCorsi**. Préserver les crédits et licences tiers.
- Ne jamais publier d’identité civile, chemin personnel, secret, configuration, log ou sauvegarde.
- Vérifier les sources et les JAR avant livraison. Les fixtures restent fictives et neutres.
- Le mod reste indépendant des autres mods Tropimon. Cobblemon demeure une dépendance officielle autorisée.
- Avant tout commit autorisé, vérifier l’auteur et le committer FastedCorsi avec une adresse GitHub noreply déjà vérifiée, sans modifier Git global.
- Produire deux JAR d’une même version validée : local avec installation différée sûre et partageable propre.
- Ne jamais remplacer un JAR lorsque Minecraft tourne ou forcer l’arrêt du jeu/launcher.
- Garder le code simple, lisible et efficace, sans abstraction, cache ou thread sans besoin démontré.
- Les résultats du casino sont aléatoires selon une table publique et stable ; jamais adaptés à un joueur.
- Les achats et retraits en Poké$ ne sont validés qu’après confirmation fiable ou action administrative auditée.
- Le super-admin gère les admins. Aucun admin ne valide sa propre opération financière.

## Publication et mise à jour autonome

- Chaque version livrée est poussée sur le dépôt GitHub public propre à ce mod, puis publiée dans une Release dont le tag correspond exactement à la version.
- La Release contient un seul JAR partageable vérifié et son fichier SHA-256. Les JAR LOCAL, configurations et scripts propres à une machine ne sont jamais publiés.
- Ce mod embarque sa propre implémentation de mise à jour. Elle ne dépend d'aucune classe, bibliothèque ou service interne d'un autre mod Tropimon.
- La mise à jour accepte uniquement la Release officielle de ce dépôt, exige le SHA-256, vérifie l'identifiant et la version de fabric.mod.json, prépare le fichier hors du dossier mods, puis remplace l'ancien JAR seulement après l'arrêt de Minecraft. Elle ne force jamais l'arrêt du jeu ou du launcher et conserve une sauvegarde hors des mods chargés.
- Une évolution de l'updater doit rester légère, asynchrone et sans travail répété par tick ou par frame.

