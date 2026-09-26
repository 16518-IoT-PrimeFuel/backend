SET @admin_username = 'replace-with-admin-username';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.username = @admin_username
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
