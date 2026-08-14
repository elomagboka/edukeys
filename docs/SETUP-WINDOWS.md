# Environnement de développement — Windows

Toute la stack du projet fonctionne nativement sur Windows. WSL n'est pas
nécessaire (voir la fin de ce guide pour savoir quand il le devient).

---

## 1. Installer les outils

Ouvre **PowerShell** (pas l'invite de commandes) et lance :

```powershell
winget install Git.Git
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
winget install OpenJS.NodeJS.LTS
winget install Docker.DockerDesktop
```

Puis **ferme et rouvre PowerShell** — les variables d'environnement ne sont
pas rechargées dans une session déjà ouverte. C'est la cause numéro un des
« commande introuvable » qui suivent une installation.

Vérification :

```powershell
java -version    # 21.x
mvn -version
node -v          # 22.x
docker --version
git --version
```

## 2. Activer les chemins longs

Windows limite historiquement les chemins à 260 caractères. Maven et
`node_modules` dépassent régulièrement cette limite, avec des erreurs de
fichier introuvable difficiles à interpréter.

Dans **PowerShell en administrateur** :

```powershell
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
  -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
```

Puis, dans une session normale :

```powershell
git config --global core.longpaths true
```

Redémarre la machine pour que le réglage système prenne effet.

## 3. Configurer Git pour les fins de ligne

Le fichier `.gitattributes` du projet gère l'essentiel, mais il faut aligner
la configuration globale :

```powershell
git config --global core.autocrlf false
git config --global init.defaultBranch main
git config --global user.name "Ton Nom"
git config --global user.email "ton.email@exemple.com"
```

**Pourquoi c'est important** : par défaut, Git convertit les fichiers en CRLF
sur Windows. Un script shell ou un Dockerfile committé ainsi arrive en CRLF
sur les runners Ubuntu de GitHub Actions, qui échouent avec des messages du
type `bad interpreter: /bin/sh^M`. C'est la première cause de pipeline cassé
chez un développeur Windows, et le message n'oriente pas du tout vers la vraie
cause.

## 4. Docker Desktop

Au premier lancement, Docker Desktop propose le moteur WSL 2 — **accepte-le**,
c'est nettement plus performant que Hyper-V. Il installera WSL en arrière-plan
sans que tu aies à travailler dedans.

Si l'installation échoue, la virtualisation est probablement désactivée dans
le BIOS/UEFI : chercher *Intel VT-x* ou *AMD-V* et l'activer.

Vérification, Docker Desktop démarré :

```powershell
docker run --rm hello-world
```

Testcontainers (utilisé par les tests d'intégration) détecte Docker Desktop
automatiquement. Aucune configuration supplémentaire n'est nécessaire.

> Laisse Docker Desktop démarrer avec Windows. Sinon, chaque `mvn verify`
> échouera tant que tu ne l'auras pas lancé à la main, avec un message peu
> parlant sur un socket inaccessible.

## 5. Poser le projet

```powershell
cd $HOME\Documents
mkdir edukeys
cd edukeys
Expand-Archive -Path "$HOME\Downloads\edukeys-scaffold.zip" -DestinationPath .
Get-ChildItem -Force        # équivalent de ls -la, montre .claude et .github
```

Édite `CLAUDE.md` pour remplacer `<org>`, puis :

```powershell
git init
git add .
git commit -m "chore: scaffold initial"
```

**Évite les chemins contenant des espaces ou des accents.**
`C:\Users\Prénom Nom\Mes Documents\...` provoque des ennuis avec Maven et
certains outils Node. `C:\dev\edukeys` est un choix plus sûr.

## 6. Claude Code

```powershell
irm https://claude.ai/install.ps1 | iex
```

Git for Windows étant installé (étape 1), Claude Code utilisera Git Bash pour
exécuter les commandes shell — ce qui rend les commandes de type Unix
utilisables. Sans lui, il se rabat sur PowerShell et certaines choses se
comportent différemment.

Si `claude` n'est pas reconnu après installation, ajoute
`%USERPROFILE%\.local\bin` à ton PATH, puis lance `claude doctor` pour
diagnostiquer.

---

## Deux points à connaître pour la suite

**Les commandes du guide de déploiement.** `docs/RENDER-SETUP.md` contient
quelques commandes écrites pour bash. Sur Windows, ouvre **Git Bash** (installé
avec Git for Windows) plutôt que PowerShell pour les exécuter telles quelles :
les retours à la ligne avec `\` n'y fonctionnent pas, PowerShell utilisant le
backtick.

L'essentiel de la configuration Render se fait toutefois dans une interface
web, pas en ligne de commande.

**Le backend tournera sur Linux en production.** Ton conteneur est basé sur une
image Linux, alors que tu développes sur Windows. Conséquences à garder en
tête : la casse des noms de fichiers est ignorée par Windows mais pas par
Linux — un import `./Components/Button` qui fonctionne chez toi cassera le
build en CI si le dossier s'appelle `components`. C'est un classique, et le
message d'erreur est clair une fois qu'on connaît la cause.

---

## Quand passer à WSL 2

Le natif suffit pour ce projet. WSL 2 devient préférable si :

- tu veux l'exécution en bac à sable de Claude Code, qui ne fonctionne pas en
  natif Windows ;
- tu rencontres des serveurs MCP qui ne s'exécutent qu'en environnement Unix ;
- les différences Windows/Linux te font perdre plus de temps que le passage à
  WSL n'en coûterait.

Si tu franchis le pas, une règle absolue : **place le projet dans le système
de fichiers WSL** (`~/projets/...`), jamais sous `/mnt/c/`. Le passage par la
frontière de fichiers rend Maven et npm plusieurs fois plus lents.
