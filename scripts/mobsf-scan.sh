#!/bin/zsh
# Starts MobSF (Mobile Security Framework) locally to scan Meldo's APK.
# MobSF only reads the APK file; it never talks to the phone.
# Runs natively from ~/mobsf (no Docker: its VM can't reach its disks on this Mac).
# First install: uv venv --python 3.13 ~/mobsf/.venv && uv pip install --python ~/mobsf/.venv/bin/python mobsf
# The pip package creates no login; after the first start, add the local one (mobsf / mobsf):
#   DJANGO_SETTINGS_MODULE=mobsf.MobSF.settings DJANGO_SUPERUSER_PASSWORD=mobsf ~/mobsf/.venv/bin/python -c \
#     "import django; django.setup(); from django.core.management import call_command; call_command('createsuperuser', interactive=False, username='mobsf', email='mobsf@localhost')"
set -eu
mobsf=~/mobsf/.venv/bin/mobsf
if [[ ! -x $mobsf ]]; then
  echo "MobSF isn't installed. Run the install line at the top of this script first."
  exit 1
fi
echo "When it says 'Listening', open http://127.0.0.1:8000"
echo "Log in with mobsf / mobsf, then drop dist/Meldo-android.apk onto the page. Ctrl-C stops it."
# Bound to localhost only, so nothing on the network can reach it.
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/jdk-21.0.13+9/Contents/Home}"
exec $mobsf 127.0.0.1:8000
