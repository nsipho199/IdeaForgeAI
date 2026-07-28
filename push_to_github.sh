#!/system/bin/sh
# Run this script on AndroidIDE to create the GitHub repo and push
# Usage: sh push_to_github.sh
# Requires GITHUB_TOKEN env var set

if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: GITHUB_TOKEN not set"
  echo "Usage: export GITHUB_TOKEN=ghp_... && sh push_to_github.sh"
  exit 1
fi

USER="nsipho199"
REPO="IdeaForgeAI"

echo "=== Creating GitHub repository: $USER/$REPO ==="

# Create the repo via GitHub API
curl -s -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"$REPO\",\"description\":\"Autonomous AI Software Engineer for Android - generates, builds, and self-heals Android apps via Gemini AI\",\"private\":false}" \
  "https://api.github.com/user/repos"

echo ""
echo "=== Pushing source code ==="
git remote set-url origin "https://$USER:${GITHUB_TOKEN}@github.com/$USER/$REPO.git"
git push -u origin master

echo ""
echo "=== Done ==="
echo "Repo: https://github.com/$USER/$REPO"