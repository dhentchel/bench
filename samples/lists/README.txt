This directory contains word lists used by the Template Message Generator, implemented by
the bench.gen package.

The lists are sampled by the generator script command:
   <?gen.words range=100to200 source=samples/lists/EnglishWords.txt ?>

The above example would insert a set of between 100 and 200 words extracted from the text file into the
output. Word selection within the file list would be based on the default "zipf" distribution, which 
biases the occurrence of words to the words near the beginning of the list, based on a mathematical
harmonic sequence frequency distribution.

See example template generator files in the samples/templates directory.

You can use your own word lists and reference them with a perf.words template instruction. The word list
parser uses a standard Java tokenizer, so you should follow these rules in building such files:
  1. you can use comments in any of the following formats:
        # comment line beginning wish hash symbol
        word1 // comment appended to line using C style comment syntax
        /*  comment appearing anywhere, using C++ / Java comment syntax  */
  2. the space, quote and apostrophe characters are token delimiters; if the string includes any of
     these you need to enclose the whole string in a different style of quotes.  For example:
        "ad hoc"
        "Jim's House"
        'I am "frustrated" about this'
  3. put the most common words at the front of the list; this way if the user chooses the "zipf" or
     "expN" distributions they will be more highly weighted in the output.

There is a full explanation of all the template generation instructions in the package notes, which are
available within the API documentation, or directly accessible at doc/com/progress/perf/gen/package.html

The last word of advice is to simply experiment with different usages of the template generator until
you get results that look right. For an example of how to run the template generator in standalone mode,
see the samples/testGen/generate_template_files.sh script.
