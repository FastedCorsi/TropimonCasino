# Backend Supabase

- `tropimon_casino.sql` crée ou remet à niveau un backend complet.
- `upgrade_0_6_0.sql` met à niveau une base 0.5.x sans supprimer les profils,
  soldes, tirages, opérations ni journaux.

Pour un nouveau projet, le hash du secret d’activation super-admin doit être
injecté manuellement dans l’éditeur SQL, puis le secret transmis localement au
premier super-admin. Aucun secret ou hash réutilisable n’est versionné :

```sql
update tropimon_casino.settings
set bootstrap_hash=extensions.digest(convert_to('<SECRET_LOCAL_UNIQUE>','UTF8'),'sha256')
where singleton=true and bootstrap_hash is null;
```

Après l’activation, le backend efface ce hash. Une réapplication de la migration
ne le recrée pas.

Avant une restauration ou une migration destructive, exporter séparément les
données avec les sauvegardes Supabase ou `pg_dump`. Ces exports contiennent des
données de joueurs et doivent rester hors du dépôt et des JAR.
