# sportsball

FIXME: description

## Installation

Download from http://example.com/FIXME.

## Usage

FIXME: explanation

    $ java -jar sportsball-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

## Examples

## Configuration

The configuration is specified in the project's `config.edn` file. Each top
level key in config map corresponds to a logical component in the app.
Dependencies between these components are expressed using the `#ig/ref` tag.
E.g. if a component map has an entry like `:app/bar {:foo-dep #ig/reg :app/foo}`
it means that the `:app/bar` component is dependent on `:app/foo`. Below is an
example of the app config, namespaced keys are used to indicate where the various
components are found throughout the app.

```
{:storage/db {:dbtype "postgres"
              :user <user>
              :port <port>
              :password <pass>
              :dbname "sportsball"}

 :slack/conn-info {:url <required-env-var: SPORTSBALL_SLACK_POST_URL_FILE>
                   :bot-token <required-env-var: SPORTSBALL_SLACK_BOT_TOKEN>
                   :channel "sports"}

 :app/logging {<required-env-var: SPORTSBALL_LOG_FILE>}

 :app/config {:alert-registry nil
              :db #ig/ref :storage/db
              :slack-conn-info #ig/ref :slack/conn-info}

 :app/routes {:config #ig/ref :app/config}

 :app/jetty {:jetty-conf {:port 3000 :join? false}
             :app-routes #ig/ref :app/routes}

 :app/task-scheduler {:core-threads 4}

 :app/scraper {:scheduler #ig/ref :app/task-scheduler
               :config #ig/ref :app/config
               :scrape-interval #or [#env SPORTSBALL_SCRAPE_INTERVAL_MINS 5]}}
```
The [Aero](https://github.com/juxt/aero) library is used when parsing the config.
Aero uses tagged literals to customize the behavior when parsing different config
elements. Examples of custom defined tags specific to this app can be found in
`src/sporsball/config.clj`.

Once the config is loaded it's passed to [Integrant](https://github.com/weavejester/integrant) a lifecycle management
library which then initializes the various app components described in the config
taking the order of dependencies into account.

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2021 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
