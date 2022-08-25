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

 :slack/conn-info {:URL <required-env-var: SPORTSBALL_SLACK_POST_URL_FILE>
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

### Slack Configuration

This app uses a Slack workspace to handle all user interaction. As a result
it expects various elements to be enabled in the Slack workspace in order
to function properly. Below is a list of Slack settings that the app expects and
a list of interactive commands offered in the chosen Slack channel.

#### Required Slack Settings
* A `sportsball-bot` app must be created and then installed in a Slack workspace.
  A Slack app can be generated for a workspace [here](https://api.slack.com/apps).

* A channel URL must be provided to the `sportsball` app via the
  `:slack/conn-info {:URL ...}` section of the config. This URL can be created
  in the `incoming webhook` section of the Slack workspace. This URL controls
  where `register-game-alert` messages will be sent.

* A request URL must be registered with Slack. This URL will be sent a request
  any time a user interacts with a button in Slack. This URL can be set in the
  Slack `Interactivity & shortcuts` section. For now the URL should use the
  `slack-alert-sub` endpoint.

* An `oauth` token with the following scopes for the workspace needs to be
  created. This token is the value for the `:slack/conn-info {:bot-token ...}`
  config setting described above. Required scopes: `channels:read`,
  `chat:write`, `commands`, `files:write`, `incoming-webhook`.

* Each slash command below needs to be registered in the Slack workspace
  and given the URL where the `sportsball` app is listening. This can be done
  in the `Slash Commands` tab of your Slack workspace. See below for which
  `sportsball` routes map to which slash commands.

#### Slack Slash Commands
* `/export-csv <optional <start-date> <end-date> >` - This command will send a
  dump of the contents of the odds table for the specified date range. If no
  date range is provided the app will attempt to send the entire contents of the
  odds table. Slack has a `1MB` limit on file links so specifing a date range is
  recommended. This slash command hits the `sportsball` `export-csv` endpoint,
  this command must be created in Slack pointing at this endpoint.

* `/register-game-alert` - This command will send an alert form via a Slack
   message. This form allows a user to pick two teams playing each other on a
   given day and set a threshold value in american odds when they would like an
   alert to be sent to the channel. During scraping if the threshold value for
   either team is crossed, i.e. the odds are less expensive than the threshold,
   a message will be sent to the Slack channel alerting the user. Currently all
   alert registrations assume that the game is on the same day that the alert
   is registered. This slash command hits the `sportsball` `slack-alert-sub`
   endpoint, this command must be created in Slack pointing at this endpoint.

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
