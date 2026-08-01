import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    rules: {
      // The schemas barrel (src/schemas/index.js) re-exports every domain's Zod schemas
      // statically. z.object(...) calls are top-level side effects the bundler can't prove
      // pure, so importing from the barrel drags every domain's schema code into whichever
      // chunk imports it — e.g. it once pulled classroom/report/semester schemas into the
      // unauthenticated login bundle. Import the concrete file instead (e.g.
      // '../schemas/login/loginRequest.js') or, for a single domain, its own barrel
      // (e.g. '../schemas/classroom.js') — those stay allowed since their consumers already
      // load that domain.
      //
      // Uses `regex`, not `group`: no-restricted-imports' `group` matches import specifiers
      // gitignore-style (via the `ignore` package), where an unanchored directory-name
      // pattern like '**/schemas' also matches — and blocks — every path *underneath* it,
      // including legitimate domain imports like '../schemas/classroom'. `regex` does a
      // plain RegExp test with no such directory-swallowing behavior.
      'no-restricted-imports': ['error', {
        patterns: [{
          regex: '(^|/)schemas/?(index(\\.js)?)?$',
          message: 'No importes desde el barrel de schemas: reintroduce la fuga de bundle (los z.object() no se pueden tree-shakear). Usa imports profundos por archivo, p. ej. ../schemas/login/loginRequest.js',
        }],
      }],
    },
  },
]);