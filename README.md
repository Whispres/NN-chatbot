# NN-chatbot

This is a chatbot both designed for neural network interraction in addition to the default command-based system. Note that it's still a work in progress, so there are bound to be bugs.

# Documentation notice

The documentation is currently beign written, and some of it is completely outdated. The readme is up to date (more or less), but it still needs editing (the same as the rest of the documentation). See [#47](https://github.com/LunarWatcher/NN-chatbot/issues/47)

# Install

## Dependencies

* discord.py - not necessary if you're using the Java backend
* numpy
* tensorlayer
* tensorflow (**`tensorflow-gpu` is recommended** - CPU is extremely slow)
* sklearn
* tensorboard (will be added layer)
* asyncio
* nltk
* Python 3.6 (anything under 3.5 requires code edits because of the async keyword and type hints. However, 3.6 is the only tested version)
* Java 8 - The Java module downloads its dependencies as .jars using Gradle. Just run it, Gradle will take care of the rest

### Please note:

In some cases, the dependencies for the depencencies of this project doesn't get installed. I.e. Tensorlayer requires Scipy, which for some reason doesn't get installed. Should this happen, `pip install` the missing packages. Using `pip` on the package installs should be enough to avoid missing dependencies. However, should there still be missing dependencies, manually install them. 

## Dataset

The [Corell Movie Dialog Corpus](http://www.cs.cornell.edu/~cristian/Cornell_Movie-Dialogs_Corpus.html) is the dataset the bot is designed for at the moment. There will be support for custom dataset (and conversation scrapping for personalized conversations), but that's an issue for later.

## Setup



The text files in the corell movie dialog corpus goes in a directory called `raw_data`. 

Rename `Config.py_example` to `Config.py` and fill in the necessary values. The bot is designed to run on all three sites, in addition to inthe console, so there is currently no system to handle missing sites. You can of course run it in the console and not deal with websites and API's.

And finally, when all the data is added, run `bot.py`. It'll set up the necessary files and start training once it's done. Checkpoints are saved every epoch (and it overrides the past save).

## Running it

The bot is split in three:

* NN backend
* Python bot
* Java bot

The Java bot and Python bot both depend on the NN backend, but can also be run without it. The Python bot has to be run in order to use the console feature.

After training, run BotCore.java in `chatbot/` to use the Java core. Or use `bot.py` with `--training false --mode 1` to use the Python one. It's the Java-core that's actively being developed (with new commands) but the Python one also works. 

The `bot.py` file supports CLI arguments. They are:

    --help     | shows the help message and exits
    --training | whether or not the bot trains (boolean)
    --mode     | The mode to run in (int). 0 for console, 1 for python bot, 2 for flask server.
    
Running with mode 2 also makes the Java program not create a new instance of the nn backend, but the server has to be up for this to happen. The server doesn't have to be manually booted at all though, as the Java program takes care of it

### ***NOTE:***

It's recommended that you cd into the root directory when running bot.py. It's the root directory the compiled .jar file will run from, meaning it calls the bot.py file relative to that. And when the bot.py file is called from the root directory, that is considered the active classpath (probably the wrong word) for the python script as well, meaning it looks for folders and files in `rootDir/`, not `rootDir/Network/`.

## System

The code is based on a bag of words, in order to be able to vectorize the words properly. 

# Known issues

* Changing the vocab size prevents loading of previous save

Unfortunately, by design. The Embedding layer takes a fixed input which is saved in the checkpoint, meaning changes in dimensions will prevent loading of the previous model. Figuring out a way to get an expanding vocab is a top priority.

* TensorFlow throws OutOfMemoryErrors

The easy way: Reduce the input dim or the vocab size. 
The hard way: Get a better GPU with more vram, or get more GPUs. If you use the CPU, get more RAM.

* Nonsense replies

Train more. 

* No memory

[Image showing the differences between some of the core chatbot types](https://www.marutitech.com/wp-content/uploads/2017/04/Chatbot-conversation-framework.png) (not embedded because it didn't load properly)

General chatbots are incredibly hard to make. Creating knowledge-based generative models isn't exactly something that's documented in the tensorflow documentation (or keras for that matter). For now, getting sensible replies is the top priority, along with expandable vocabulary. Adding generative memory (not retrieval-based) with context is a task for later

* Training is slow

Use a GPU if possible. If you already are, use more. Or decrease the vocab size, it speeds it up a little.


# Notes

With the Java/Kotlin bot, using the revision command requires access to git. Meaning it has to be added to the path or in some
other way become accessible to the program.

# Licensing

    Copyright 2018 LunarWatcher

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
