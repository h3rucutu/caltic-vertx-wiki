---
--- Take this script with a grain of salt and adapt it to your RDBMS
---

CREATE TABLE IF NOT EXISTS "user" (
  username VARCHAR(32) NOT NULL PRIMARY KEY,
  password VARCHAR(255) NOT NULL, password_salt VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
  username VARCHAR(32) NOT NULL,
  role VARCHAR (255) NOT NULL
);

CREATE TABLE IF NOT EXISTS roles_perms (
  role VARCHAR(255) NOT NULL,
  perm VARCHAR(255) NOT NULL
);


---
--- Dummy user:
---  username: admin  | password: admin  | role : admin  | perm : create, update, delete
---  username: editor | password: editor | role : editor | perm : create, update, delete
---  username: writer | password: writer | role : writer | perm : update
---

INSERT INTO "user" VALUES (
  'admin',
  '948263F959F38DB351A77064B2DC4C5BACA638C1EFD24B93436553FE88AD58632E5B83118F7A7FC0128FB7BA70DBCF3ADD9798A7305450CC409349E0A12DA91F',
  '7CA0E29F1F2E4C7047643EEA3A716A580895C1473461189EC6CED3D3FCCA4264'
);

INSERT INTO "user" VALUES (
  'editor',
  'B6061BB76330EF826E0A10C0545F34C57843B108B1172694ED8356A882227C4A8286EC0D26648B5453644C362E0BE117F515091E63F0B42B36492856817AB2FD',
  '38E20EE2FE1C68F2C649447181274496E6A85597ECC9F97F505EFCE1A156EE72'
);

INSERT INTO "user" VALUES (
  'writer',
  'B222852A3FBC5E1D037AA0B3C0B836EC0749CE2364EE76160E3EA32B38A472D32FD150804ADD425AAE0EAAE17967739E1C9FD4DBB2732ED1D04F74823046D815',
  '357A9EB27F41D15CC6FF1B05987B5C862A331E9B75C51E4C3C8475C3FC0F5556'
);

INSERT INTO roles_perms VALUES ('admin', 'create');
INSERT INTO roles_perms VALUES ('admin', 'update');
INSERT INTO roles_perms VALUES ('admin', 'delete');
INSERT INTO roles_perms VALUES ('editor', 'create');
INSERT INTO roles_perms VALUES ('editor', 'update');
INSERT INTO roles_perms VALUES ('editor', 'delete');
INSERT INTO roles_perms VALUES ('writer', 'update');

INSERT INTO user_roles VALUES ('admin', 'admin');
INSERT INTO user_roles VALUES ('editor', 'editor');
INSERT INTO user_roles VALUES ('writer', 'writer');