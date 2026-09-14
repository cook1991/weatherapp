# Uploading the SkyCast page to spritzlive.net/skycast

Upload the **entire contents of this `web/` folder** to a `skycast` directory on the
site so the structure is:

```
spritzlive.net/skycast/
├── index.html
├── privacy.html
├── SkyCast-v1.1.apk
└── img/
    ├── icon.png
    ├── feature.png
    ├── shot1.png
    ├── shot2.png
    └── shot3.png
```

Then share **https://spritzlive.net/skycast/**

## Resulting URLs
- Landing / download page: `https://spritzlive.net/skycast/`
- Privacy policy (use this in the Play Console): `https://spritzlive.net/skycast/privacy.html`
- Direct APK link: `https://spritzlive.net/skycast/SkyCast-v1.1.apk`

## Plesk / Apache note — APK download MIME type
Most servers serve `.apk` fine. If clicking the download opens junk text instead of
downloading, add the correct MIME type. In the domain's Apache config or an
`.htaccess` file in the `skycast` folder:

```apache
AddType application/vnd.android.package-archive .apk
```

(The download button also uses the HTML `download` attribute, which forces a download
in modern browsers regardless.)

## Updating later
When you build a new APK, replace `SkyCast-v1.1.apk` (or add the new file and
update the two links in `index.html`). Bump the version text in `index.html`
(`v1.0`, the meta line, and the button label).
