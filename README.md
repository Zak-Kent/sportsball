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

The configuration is specified in the project's config.edn file. In that file
there is a top level map which has the following format:
```
{:db {:dbtype "postgres"
      :user <user>
      :port <port>
      :password <pass>
      :dbname "sportsball"}

 :slack {:url <required-env-var: SPORTSBALL_SLACK_POST_URL_FILE>
         :bot-token <required-env-var: SPORTSBALL_SLACK_BOT_TOKEN>}

 :logging {<required-env-var: SPORTSBALL_LOG_FILE>}}
```

All `required-env-vars` listed above must be present in the environment when the
program is run along with values for all the keys shown above.

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
