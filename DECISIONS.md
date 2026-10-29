# Decision log

The code of this repository points at the decisions below. A number is handed out once. It is never
reused or renumbered, so a citation always finds its entry. A decision which is overturned keeps its
entry. The entry is then marked as superseded and names the entry which replaced it.

Most of the code here is made from the Business Cockpit extension for Camunda 7 (decision 3). That
code cites the decisions of the Camunda 7 extension, and it says so:
`see decision 3 in the Camunda 7 extension's DECISIONS.md`. Code written for this repository cites
the entries below, in the usual form `see decision 2 in the repository's DECISIONS.md`.

### 1. The decisions of the Camunda 7 extension apply here, unless an entry below says otherwise

This extension does what the Camunda 7 extension does, with CIB seven as the engine. Its code is
made from the Camunda 7 code, and the reasons for that code are written down in the
[decision log of the Camunda 7 extension](https://github.com/vanillabp/businesscockpit-camunda7-adapter/blob/main/DECISIONS.md).
They hold here as well. Copying them would give two texts which drift apart, so they stay there.

An entry below is about one of two things. Either CIB seven differs from Camunda 7, or this
repository differs from the Camunda 7 repository. Where an entry below contradicts a decision of the
Camunda 7 extension, the entry below wins in this repository.

A change to a decision of the Camunda 7 extension reaches this repository with the next sync
(decision 3). Whoever runs the sync reads the new entries of that log and asks whether one of them
needs an entry here.

The same holds one level down. The CIB seven adapter this extension plugs into has its own
[decision log](https://github.com/vanillabp/cibseven-adapter/blob/main/DECISIONS.md), and its
decisions about the engine are the ones this extension lives with.

### 2. The extension pins the CIB seven minor of the CIB seven adapter, and there is one line of it

The CIB seven adapter pins one CIB seven minor and carries it in its own version: `2.0.0-2.2` is
VanillaBP 2.0.0 for CIB seven 2.2 (decision 3 in the CIB seven adapter's log). This extension
follows that line. Its root POM asks for the adapter in that version, and it pins CIB seven in the
property `cibseven.version` at the same release the adapter pins, today `2.2.0`. The recipe writes
both values, so they change in the recipe and nowhere else. Renovate leaves both alone, because a
change on `main` would meet the next sync.

The extension uses engine classes below `.impl.`: the parse listener, the history event handler, the
command context. CIB seven may change those in any minor. The adapter has the same problem, and it
answers it with one line which follows the newest minor. So this extension has one line too. Its
version moves with the other Business Cockpit extensions. If the adapter ever opens a second line
for an older minor, this extension follows it the way the Camunda 8 extension follows the lines of
the Camunda 8 adapter.

### 3. The code is made from the Camunda 7 extension, and a merge brings each change over

The Camunda 7 extension keeps changing, and nearly all of its changes are wanted here too. Porting
each of them by hand would cost the most and would miss some. So the code is made again from a commit
of the Camunda 7 extension whenever it is to follow. This is the procedure of the CIB seven adapter,
applied to the extension:

- `bin/sync-from-camunda7.sh` copies the tree of the Camunda 7 extension and runs the OpenRewrite
  recipe `rewrite/vanillabp-cibseven.yml` over it. The recipe renames the engine's packages and its
  jar, pins the CIB seven version (decision 2) and writes VanillaBP's own names (decision 4). The
  script commits the result to the branch `generated` as `generated from BC C7 <sha>`.
- `generated` holds nothing else. Nobody edits it by hand. `main` merges it. Everything this
  repository does differently sits on `main` as ordinary commits on top, so a conflict appears only
  where a change of the Camunda 7 extension touches one of those places.
- Files which `main` writes itself are never copied: the workflows, `README.md`, `DECISIONS.md`,
  `AGENTS.md`, `CONTRIBUTING.md`, `CLAUDE.md`, `UPGRADE.md` and `renovate.json`. The script lists
  them.
- The workflow `sync-from-camunda7.yaml` runs the script every night against `main` of the Camunda 7
  extension. When the result differs, it opens a pull request. The CI of the Camunda 7 extension
  knows nothing of this repository, so a change there is never held up by CIB seven.

A fix of generated code goes into the recipe, not onto `main`. Then `generated` itself builds, and a
merge never meets a fix by hand. The first sync needed no such fix: `generated` passes every test of
the Camunda 7 extension as it is. What `main` adds on top is this repository's own: the
documentation, the workflows and the module `migration-tests` (decision 5). `CONTRIBUTING.md` holds
the procedure and what to check after each sync.

CIB seven publishes a recipe of its own, `org.cibseven.ReplaceCamundaWithCIBSeven`. It is not used,
for the reason the CIB seven adapter gives in its decision 2: it renames every `org.camunda`, so it
would also rename the groupId `org.camunda.community.vanillabp` of the Camunda 7 adapter.

A test class of the Camunda 7 extension keeps the parts of its name which are not `Camunda7`, and
its test resources keep their paths, such as `c7-cockpit/processes`. A renamed path is one more line
which can conflict in a merge.

### 4. The extension has a name of its own everywhere

The extension must be able to run next to the Camunda 7 extension in one application. That is how
an application moves from Camunda 7 to CIB seven. Two classes with the same name on one classpath,
two Spring auto-configurations with the same name, or two Quarkus extensions with the same name
break that. So nothing of this extension carries the name of the Camunda 7 extension:

|          What          |                  Camunda 7 extension                  |            This extension             |
|------------------------|-------------------------------------------------------|---------------------------------------|
| Maven groupId          | `io.vanillabp.businesscockpit`                        | `io.vanillabp.businesscockpit`        |
| artifactIds            | `businesscockpit-camunda7-adapter...`                 | `businesscockpit-cibseven-adapter...` |
| Java package           | `io.vanillabp.cockpit.camunda7`                       | `io.vanillabp.cockpit.cibseven`       |
| class names            | `Camunda7...`                                         | `CibSeven...`                         |
| Quarkus extension      | `vanillabp-business-cockpit-camunda7`                 | `vanillabp-business-cockpit-cibseven` |
| adapter it plugs into  | `org.camunda.community.vanillabp:camunda7-adapter...` | `io.vanillabp:cibseven-adapter...`    |
| adapter type it serves | `camunda7`                                            | `cibseven`                            |

The groupId stays the one of the Business Cockpit, like the other extensions. The recipe
`rewrite/vanillabp-cibseven.yml` writes all of these names, so a sync keeps them. The module
`migration-tests` runs both extensions in one application (decision 5).

### 5. The tests with the Camunda 7 extension are a module of their own

`migration-tests` runs this extension and the Camunda 7 extension in one application, each with its
adapter and its engine. It has one submodule per platform, `migration-tests/spring-boot` and
`migration-tests/quarkus`. It is a module of its own and not part of `spring-boot` or `quarkus`, for
the reasons the CIB seven adapter gives in its decision 8:

- `spring-boot` and `quarkus` come from the Camunda 7 extension. Their POMs change with every sync,
  and a module of this repository's own never meets a conflict there.
- The Camunda 7 adapter is a snapshot, and its snapshots are only in the GitHub Packages registry of
  the Camunda Community Hub, which wants a login even for reading. A module of its own keeps that
  need to one place. Every module before it builds without the login.

The module names that registry under the id `camunda-community-hub-vanillabp-camunda7-adapter`. A
build needs a server entry with that id and a token with `read:packages`. Once the Camunda 7 adapter
has a release on Maven Central, the registry goes away.

Each engine gets a datasource of its own. Two engines on one set of tables are refused, and the CIB
seven adapter checks that across the adapter types as well. On Quarkus both datasources are XA
datasources. VanillaBP's outbox starts a workflow on the CIB seven engine in a transaction which also
writes the outbox table of the application's datasource. Without XA that transaction refuses the
second datasource, and the start is tried again and again. Camunda 7.24 and CIB seven 2.2 bring the same FEEL engine, so the two engines need
nothing else to start in one application.

The modules publish nothing. Each builds its empty jar and leaves itself out of the bundle for Maven
Central with `excludeArtifacts`, because the publishing plugin stops the whole publish when a module
of the reactor assigns no file. They have no coverage of their own in the reports. Their tests cover
code which `spring-boot` and `quarkus` already cover, and they would add the classes of the Camunda 7
extension to the reports.
