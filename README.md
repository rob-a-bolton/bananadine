# bananadine

A Matrix bot written in Clojure

## Installation

Clone the repo and run `lein deps`

## Usage

Run `lein run -- -c config.edn` to start the app  
Config is expected to be an edn file containing keys to specify mongodb credentials and matrix account credentials.  
An example config is provided in the repo, and it is *strongly advised* to change these credentials before use.  

To register a user or update stored credentials, use `--register` or `--update` respectively.  
Note successful registration will require a host domain `-d`, a user `-u`, and a password `-p`.  
Host domain is the domain of the host, not the full URL. Matrix instances hosted on non-root URLs are not currently supported (e.g. `https://my-cool-host.com/matrix-instance/`).  
Providing the user credentials as CLI options is very insecure, so you probably shouldn't run this bot anywhere for any reason in its current form.  
This shall change in the future when I get around to moving provision of these credentials into environment variables.  

A running mongodb with a user created for the bot must be accessible. Check the example `config.edn` and change the values accordingly.


The canonical way to develop (and currently to run) this bot is using CIDER/Emacs (or another NREPL workflow).  
Require `[omniconf.core :as cfg]` then load your config with `(cfg/populate-from-file "config.edn")` first, then require `[bananadine.bootstrap :refer [default-handlers]]` and `mount.core :refer start`, followed by `(start)` to initialise the component system and start handling events.

Despite this, it is also possible to simply run `lein run -c config.edn` after you have registered the bot with the matrix server.  
This will run the bot in the foreground with no forks.

## License

Copyright © 2021 Rob A Bolton

This file is part of Bananadine

Bananadine is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Bananadine is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Bananadine.  If not, see https://www.gnu.org/licenses/
