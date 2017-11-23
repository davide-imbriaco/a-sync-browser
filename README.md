# Syncthing Lite

[![MPLv2 License](https://img.shields.io/badge/license-MPLv2-blue.svg?style=flat-square)](https://www.mozilla.org/MPL/2.0/)

[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=net.syncthing.lite)

This project is an Android app, that works as a client for a [Syncthing][1] share (accessing 
Syncthing devices in the same way a client-server file sharing app access its proprietary server). 

This project is based on [a-sync][2], a java implementation of Syncthing protocols (bep, 
discovery, relay). 

NOTE: this is a client-oriented implementation, designed to work online by downloading and 
uploading files from an active device on the network (instead of synchronizing a local copy of 
the entire repository). This is quite different from the way the original Syncthing app works, 
and its useful from those devices that cannot or wish not to download the entire repository (for 
example, mobile devices with limited storage available, wishing to access a syncthing share).

All code is licensed under the [MPLv2 License][3].

[1]: https://syncthing.net/
[2]: https://github.com/davide-imbriaco/a-sync
[3]: https://github.com/davide-imbriaco/a-sync-browser/blob/master/LICENSE
[4]: https://play.google.com/store/apps/details?id=it.anyplace.syncbrowser
