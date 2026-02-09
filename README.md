Signalement Universel France 🇫🇷

Une application Android citoyenne pour simplifier le signalement des problèmes de voirie dans les 35 000 communes de France.

⚠️ AVIS IMPORTANT / DISCLAIMER

Cette application est une initiative privée et indépendante. Elle ne représente PAS une entité gouvernementale, une mairie ou l'État français.

This application is a private and independent initiative. It does NOT represent any government entity, municipality, or the French State.

📱 À propos du projet
Signalement Universel France est un outil de facilitation conçu pour aider les citoyens à contacter les services techniques de leur mairie. L'application agit comme une passerelle intelligente :

Elle géolocalise l'utilisateur.

Elle identifie la commune compétente via des données publiques (Open Data).

Elle génère un email pré-rempli avec la photo et la position GPS du problème.

L'objectif est de simplifier la démocratie participative sans nécessiter d'inscription ni de base de données centrale.

✨ Fonctionnalités
📍 Géolocalisation précise : Utilisation du GPS pour situer l'incident.

🏛️ Annuaire universel : Fonctionne dans toute la France (Métropole & DROM) grâce aux API de l'État.

📸 Preuve par l'image : Prise de photo ou import depuis la galerie.

🗺️ Cartographie Open Source : Utilisation d'OpenStreetMap (via OSMDroid) pour le respect de la vie privée.

📧 Envoi direct : Pas de serveur intermédiaire, l'email part depuis la messagerie de l'utilisateur.

🔒 Privacy by design : Aucune création de compte, aucune collecte de données personnelles.

🛠 Stack Technique
Ce projet est développé en Kotlin natif.

Architecture : MVVM (Model-View-ViewModel)

Asynchronisme : Coroutines & Kotlin Flow

Réseau : Ktor Client (avec moteur OkHttp) & Kotlinx Serialization

Cartographie : OSMDroid (OpenStreetMap for Android)

Paiement : Google Play Billing Library (pour le mode Premium)

UI : Android Views (XML) & Material Design Components

🏛️ Sources des données (Open Data)
La transparence est au cœur de ce projet. L'application n'héberge aucune donnée propriétaire sur les mairies. Elle interroge en temps réel les API publiques de l'État français (plateforme api.gouv.fr).

Les données proviennent exclusivement de :

API Découpage Administratif (GeoAPI) : Pour lier une coordonnée GPS à un code INSEE.

Source : geo.api.gouv.fr

API de l'Annuaire de l'Administration (Service Public) : Pour obtenir l'email des services techniques.

Source : api-lannuaire.service-public.fr

API Établissements Publics : Source complémentaire pour les coordonnées.

Source : etablissements-publics.api.gouv.fr

🚀 Installation et Build
Pour compiler ce projet localement :

Clonez le dépôt :

Bash
git clone https://github.com/clemPerrousset/signalement-universel-france.git
Ouvrez le projet dans Android Studio.

Laissez Gradle synchroniser les dépendances.

Compilez et lancez sur un émulateur ou un appareil physique.

Note : Les clés de signature (Keystore) ne sont pas incluses dans ce dépôt.

📄 Licence
Ce projet est distribué sous licence MIT. Vous êtes libre de consulter, modifier et redistribuer ce code, à condition de conserver la mention de l'auteur original.

Copyright (c) 2027 Clément Perrousset.

Développé avec ❤️ pour améliorer nos villes.
