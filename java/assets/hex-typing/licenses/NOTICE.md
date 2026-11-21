# Hex typing backport

The spatial decoder, word policy and model reader are adapted from Hex for
Android at commit `d23acdf8ca91ab3a4f1790ddff6263dc908d4e97`. The backport
feeds them FUTO's keyboard geometry and tap coordinates, and uses FUTO's
suggestion strip and correction handling. See [Hex-MIT.txt](Hex-MIT.txt).

- `en.hextm` and `de.hextm` come from Hex and are derived from Google Books
  Ngram Viewer v3 (2020-02-17) by Google and the Google Books Ngram
  contributors. Hex lowercased and summed the 1980–2019 counts, filtered
  spellings, selected word pairs and quantized probabilities. [Dataset](https://storage.googleapis.com/books/ngrams/books/datasetsv3.html) · [CC BY 3.0](google-books-cc-by-3.0.txt)
- `en.valid` contains lowercased and sorted English spellings outside the
  frequency model, from SCOWL 2020.12.07 through size 70, by Kevin Atkinson
  and contributors. The backport preserves the entries and replaces newlines
  with NUL record terminators for the memory-mapped binary asset.
  Original Hex SHA-256: `d2b7c27ddd7da701cab7bffd1468f51061bf569d7dc81bfcaacf29beeea0d6cf`.
  [Source](https://wordlist.aspell.net/) · [Permissions and notices](scowl.txt)
- `never-suggest.tsv` is Hex's adaptation of the List of Dirty, Naughty,
  Obscene, and Otherwise Bad Words, © 2012–2020 Shutterstock, Inc. and
  contributors. Hex lowercased and classified entries, omitted ordinary,
  medical and search-only terms, and added spellings and inflections. This
  backport includes the English, German and shared entries.
  [Source](https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/tree/5faf2ba42d7b1c0977169ec3611df25a3c08eb13) · [CC BY 4.0](cc-by-4.0.txt)
