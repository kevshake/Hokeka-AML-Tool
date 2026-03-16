# Incident Report: www.hokeka.com 500 Error

**Date:** 2026-03-13 09:03 UTC  
**Reporter:** Shakes (CEO)  
**Status:** RESOLVED

## Issue Summary
Website at https://www.hokeka.com was returning **500 Internal Server Error** after deployment.

## Root Cause
Nginx web server could not access website files due to permission restrictions:
- Website files were initially placed in `/root/.openclaw/workspace/fraud-detector/website/dist/`
- Nginx runs as `www-data` user which cannot access files in `/root/` directory
- Error logs showed: `stat() "/root/..." failed (13: Permission denied)`

## Resolution
1. Moved website files to `/var/www/hokeka-website/`
2. Changed ownership: `chown -R www-data:www-data /var/www/hokeka-website`
3. Set permissions: `chmod -R 755 /var/www/hokeka-website`
4. Updated nginx config with new document root
5. Reloaded nginx

## Configuration Change
**File:** `/etc/nginx/sites-available/hokeka-domains`

```diff
- root /root/.openclaw/workspace/fraud-detector/website/dist;
+ root /var/www/hokeka-website;
```

## Verification
```bash
ls -la /var/www/hokeka-website/
# drwxr-xr-x 2 www-data www-data 4096 Mar 13 09:08 .
# -rwxr-xr-x 1 www-data www-data 29684 Mar 13 09:08 index.html
# -rwxr-xr-x 1 www-data www-data 17406 Mar 13 09:08 styles.css
```

## Prevention
For future static website deployments:
1. Always deploy to `/var/www/[site-name]/`
2. Ensure `www-data` user has read access
3. Test with `sudo -u www-data ls -la /var/www/[site-name]/`

## Related Files
- Website source: `/root/.openclaw/workspace/fraud-detector/website/`
- Deployed files: `/var/www/hokeka-website/`
- Nginx config: `/etc/nginx/sites-available/hokeka-domains`
