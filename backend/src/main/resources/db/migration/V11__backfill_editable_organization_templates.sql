-- Ensure organizations created before template cloning was wired into the API
-- receive any missing starter configuration. clone_org_templates is
-- idempotent: existing roles, permissions, workflows and ticket types are
-- preserved, while missing copies are added. Organization-owned role copies
-- use is_template = FALSE so administrators can edit them normally.
SELECT clone_org_templates(id)
FROM organization;

-- Guard against older/manual data that may have incorrectly marked an
-- organization-owned role as a locked template.
UPDATE role
SET is_template = FALSE
WHERE organization_id IS NOT NULL
  AND is_template = TRUE;
