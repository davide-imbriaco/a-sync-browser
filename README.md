# a-sync-browser

[![MPLv2 License](https://img.shields.io/badge/license-MPLv2-blue.svg?style=flat-square)](https://www.mozilla.org/MPL/2.0/)

This project is an android app, that works as a client for a [Syncthing][1] share (accessing syncthing devices in the same way a client-server file sharing app access its proprietary server). You can find the app on [Google Play Store][4].
This project is based on [a-sync][2], a java implementation of Syncthing protocols (bep, discovery, relay). 

NOTE: this is a client-oriented implementation, designed to work online by downloading and uploading files from an active device on the network (instead of synchronizing a local copy of the entire repository). This is quite different from the way the original Syncthing app works, and its useful from those devices that cannot or wish not to download the entire repository (for example, mobile devices with limited storage available, wishing to access a syncthing share).

DISCLAMER: I'm not the owner fo Syncthing. The Syncthing name, logo and reference documentation is property of the Syncthing team, and licensed under the MPLv2 License. This project is not affiliated with Syncthing, and uses its public avaliable documentation and protocol specification as a reference to implement protocol compatibility.

Documentation is non-existing as know, if you have any question feel free to contact the developer (me).

All code is licensed under the [MPLv2 License][3].

If you like this work, please donate something to help its development!

[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=FVB93HNCH5NL8)

[1]: https://syncthing.net/
[2]: https://github.com/davide-imbriaco/a-sync
[3]: https://github.com/davide-imbriaco/a-sync-browser/blob/master/LICENSE
[4]: https://play.google.com/store/apps/details?id=it.anyplace.syncbrowser

