# Deployment

CI tests pull requests. The Deploy workflow calls the same CI on pushes to main;
all deployment operations share a non-cancelling concurrency group.

| Trigger / environment | Behavior |
| --- | --- |
| Push to main | Test, build JAR/image, deploy dev, create annotated `dev-*` tag with image metadata. |
| Manual `dev`, no `release_tag` | Build and deploy the selected workflow ref to dev. Skips tests and creates no release tag. |
| Manual `dev`, `release_tag=dev-*` | Redeploy that tag's recorded image and manifest without rebuilding. Main only. |
| Manual `prod`, no `release_tag` | Promote latest `dev-*` tag's recorded image and manifest. Main only. |
| Manual `prod`, `release_tag=dev-*` or `prod-*` | Deploy that release's recorded image and manifest. Main only. |
| Manual `dev-rebuild` / `prod-rebuild` | Rebuild latest matching environment tag, or an explicitly supplied previous tag for that environment. Main only. |
| Daily, 03:00 UTC | Independently rebuild and redeploy latest `dev-*` and `prod-*` tags, not main HEAD. |

Every successful prod promotion or rollback creates a new annotated `prod-*` tag
at the selected commit, recording the deployed image. The next scheduled prod
refresh therefore keeps the rolled-back code. Existing tags are never moved.

Dev redeployment and tagged rebuilds do not create or move release tags. Selecting
an older tag for one of these operations is temporary: the next scheduled refresh
returns to the latest deployment tag for that environment.

Refresh builds check out both code and Nais manifests from the selected release,
pull fresh base images, disable Docker build caching, and run `bootJar`. Releases
containing `fetchIpCountry` fetch the current IPtoASN snapshot on every build,
independently of Gradle's cache. Recorded-image redeployment/promotion does not
refresh its embedded country data because it does not rebuild the image.

Only tested push-to-main deployments create `dev-*` release tags. Tagged-release
refreshes and recorded-image deployments skip tests; arbitrary-ref manual dev
builds also skip tests and must not be treated as tested releases.

## First deployment and release selection

Scheduled refresh skips an environment with no deployment tag and emits a notice.
Deploy main to dev to create the first `dev-*` tag, then manually promote to prod
to create the first `prod-*` tag. Manual operations requiring a tag fail clearly
if none exists. There is no fallback to main HEAD.

`release_tag` accepts exact existing annotated deployment tags, not branches,
commit IDs, or Git expressions. Image deployments also require a single valid
Google Artifact Registry image reference in the tag's `Image:` metadata. Existing
deployment tags from the previous workflow remain supported.

The small shared resolver lives in `.github/scripts/resolve-release.sh`. Its tests
use disposable local Git repositories, make no network calls, and run in CI:

```bash
python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
```
