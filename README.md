# bananadine

A Matrix bot written in Clojure

## Installation

Clone the repo and run `lein deps`

## Usage

Run `lein run -- -c config.edn` to start the app  
Config is expected to be an edn file containing `:db-dir` and `:log-dir`  
A default config file is included in this repository, using `$CWD/db` as the log directory.  

To register a user or update stored credentials, use `--register` or `--update` respectively.  
Note successful registration will require a host domain `-d`, a user `-u`, and a password `-p`.  
Host domain is the domain of the host, not the full URL. Matrix instances hosted on non-root URLs are not currently supported (e.g. `https://my-cool-host.com/matrix-instance/`).  
Providing the user credentials as CLI options is very insecure, so you probably shouldn't run this bot anywhere for any reason in its current form.  
This shall change in the future when I get around to moving provision of these credentials into environment variables.  


The canonical way to develop (and currently to run) this bot is using CIDER/Emacs (or another NREPL workflow).  
Require `bananadine.bootstrap` and `mount.core :refer start` then `(start)` to initialise the component system and start handling events.

## License

Copyright © 2020 Rob A Bolton

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
